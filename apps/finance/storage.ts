import { Data, emptyData } from "./domain";
declare global {
  interface Window {
    VeyraFinance?: {
      request: (id: string, method: string, payload: string) => void;
    };
    omegaFinanceReply?: (id: string, result: string) => void;
    veyraBack?: () => boolean;
  }
}
const pending = new Map<
  string,
  { resolve: (value: any) => void; reject: (reason: Error) => void }
>();
window.omegaFinanceReply = (id, result) => {
  const p = pending.get(id);
  if (!p) return;
  pending.delete(id);
  try {
    const r = JSON.parse(result);
    if (r.error) p.reject(Error(r.error));
    else p.resolve(r);
  } catch {
    p.reject(Error("Resposta inválida do aplicativo."));
  }
};
export function nativeCall(method: string, payload: any = {}): Promise<any> {
  return new Promise((resolve, reject) => {
    const id = crypto.randomUUID();
    pending.set(id, { resolve, reject });
    window.VeyraFinance!.request(id, method, JSON.stringify(payload));
    setTimeout(() => {
      if (pending.delete(id))
        reject(Error("O aplicativo demorou a responder. Tente novamente."));
    }, 30000);
  });
}
const openDB = () =>
  new Promise<IDBDatabase>((resolve, reject) => {
    const r = indexedDB.open("veyra-finance-v1", 1);
    r.onupgradeneeded = () => r.result.createObjectStore("state");
    r.onsuccess = () => resolve(r.result);
    r.onerror = () =>
      reject(
        Error(
          "Não foi possível abrir seus dados locais. Permita armazenamento neste navegador.",
        ),
      );
  });
export async function load(): Promise<Data> {
  if (window.VeyraFinance) return nativeCall("snapshot");
  const db = await openDB();
  try {
    return await new Promise((resolve, reject) => {
      const tx = db.transaction("state");
      const r = tx.objectStore("state").get("main");
      r.onsuccess = () => resolve(r.result || emptyData());
      r.onerror = () => reject(r.error);
    });
  } finally {
    db.close();
  }
}
export async function persist(before: Data, next: Data): Promise<Data> {
  if (window.VeyraFinance) {
    const changed = next.items.filter(
      (i) =>
        JSON.stringify(i) !==
        JSON.stringify(before.items.find((b) => b.id === i.id)),
    );
    return nativeCall("save", {
      items: changed,
      preferences: next.preferences,
      expectedRevision: before.revision,
    });
  }
  const db = await openDB();
  try {
    return await new Promise((resolve, reject) => {
      const tx = db.transaction("state", "readwrite"),
        store = tx.objectStore("state"),
        r = store.get("main");
      let conflict = false;
      r.onsuccess = () => {
        if ((r.result?.revision || 0) !== before.revision) {
          conflict = true;
          tx.abort();
        } else store.put({ ...next, revision: before.revision + 1 }, "main");
      };
      tx.oncomplete = () => {
        if (typeof BroadcastChannel !== "undefined") {
          const channel = new BroadcastChannel("veyra-finance");
          channel.postMessage("changed");
          channel.close();
        }
        resolve({ ...next, revision: before.revision + 1 });
      };
      tx.onabort = () =>
        reject(
          Error(
            conflict
              ? "Seus dados mudaram em outra aba. Recarregue antes de continuar."
              : "Não foi possível salvar. Confira o espaço disponível.",
          ),
        );
      tx.onerror = () =>
        reject(
          Error(
            "Não foi possível salvar. Exporte um backup antes de limpar o navegador.",
          ),
        );
    });
  } finally {
    db.close();
  }
}
export async function download(name: string, text: string, mime: string) {
  if (window.VeyraFinance) {
    await nativeCall("export", { name, text, mime });
    return;
  }
  const url = URL.createObjectURL(new Blob([text], { type: mime }));
  const a = document.createElement("a");
  a.href = url;
  a.download = name;
  a.click();
  setTimeout(() => URL.revokeObjectURL(url), 30000);
}
