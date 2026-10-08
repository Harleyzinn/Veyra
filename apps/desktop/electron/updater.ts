import { createHash, verify } from "node:crypto";
import { mkdirSync, writeFileSync, readFileSync, existsSync } from "node:fs";
import { join } from "node:path";
export interface DesktopRelease {
  version: string;
  tag: string;
  notes: string;
  manifestUrl: string;
  installerUrl: string;
}
export function newer(candidate: string, current: string) {
  if (!/^\d+\.\d+\.\d+$/.test(candidate)) return false;
  const a = candidate.split(".").map(Number),
    b = current.split(".").map(Number);
  for (let i = 0; i < 3; i++) {
    if (a[i] !== b[i]) return a[i] > b[i];
  }
  return false;
}
export function trustedUrl(url: string) {
  const u = new URL(url);
  if (
    u.protocol !== "https:" ||
    u.username ||
    u.password ||
    u.hostname !== "github.com" ||
    !u.pathname.startsWith("/Harleyzinn/Veyra/releases/download/desktop-v")
  )
    throw Error("Origem da atualização não autorizada.");
  return u.toString();
}
export function verifyManifest(
  input: any,
  publicKey: string,
  release: DesktopRelease,
) {
  if (
    typeof input?.payload !== "string" ||
    typeof input?.signature !== "string" ||
    input.payload.length > 20000 ||
    !verify(
      null,
      Buffer.from(input.payload),
      publicKey,
      Buffer.from(input.signature, "base64"),
    )
  )
    throw Error("Assinatura da atualização inválida.");
  const value = JSON.parse(input.payload);
  if (
    value.version !== release.version ||
    value.url !== release.installerUrl ||
    !/^\d+\.\d+\.\d+$/.test(value.version) ||
    !/^\w[\w.-]+\.exe$/.test(value.name) ||
    !/^([a-f0-9]{64})$/.test(value.sha256) ||
    !Number.isInteger(value.size) ||
    value.size < 10000 ||
    value.size > 400000000
  )
    throw Error("Manifesto de atualização incompatível.");
  trustedUrl(value.url);
  return value as {
    version: string;
    url: string;
    name: string;
    sha256: string;
    size: number;
  };
}
export class DesktopUpdater {
  available: DesktopRelease | null = null;
  downloaded = "";
  private expectedHash = "";
  constructor(
    readonly version: string,
    private directory: string,
    private publicKey: string,
  ) {}
  async check() {
    const response = await fetch(
      "https://api.github.com/repos/Harleyzinn/Veyra/releases?per_page=30",
      {
        headers: {
          "User-Agent": "Veyra-Life-Desktop",
          Accept: "application/vnd.github+json",
        },
        signal: AbortSignal.timeout(20000),
      },
    );
    if (!response.ok)
      throw Error("Não foi possível consultar as atualizações.");
    const rows: any[] = await response.json();
    const row = rows.find(
      (r) =>
        !r.draft &&
        !r.prerelease &&
        /^desktop-v\d+\.\d+\.\d+$/.test(r.tag_name) &&
        newer(r.tag_name.slice(9), this.version),
    );
    if (!row) {
      this.available = null;
      return null;
    }
    const installer = row.assets.find(
      (a: any) => a.name === `VeyraLife-Setup-${row.tag_name.slice(9)}.exe`,
    );
    const manifest = row.assets.find(
      (a: any) => a.name === "desktop-update.json",
    );
    if (!installer || !manifest)
      throw Error("A versão publicada não possui os arquivos verificados.");
    this.available = {
      version: row.tag_name.slice(9),
      tag: row.tag_name,
      notes: String(row.body || "").slice(0, 20000),
      manifestUrl: trustedUrl(manifest.browser_download_url),
      installerUrl: trustedUrl(installer.browser_download_url),
    };
    return this.available;
  }
  verifyDownloaded() {
    if (
      !this.downloaded ||
      !this.expectedHash ||
      createHash("sha256")
        .update(readFileSync(this.downloaded))
        .digest("hex") !== this.expectedHash
    )
      throw Error("O instalador foi alterado. Baixe novamente a atualização.");
  }
  async download() {
    const release = this.available;
    if (!release) throw Error("Consulte as atualizações primeiro.");
    const response = await fetch(release.manifestUrl, {
      signal: AbortSignal.timeout(20000),
    });
    if (!response.ok) throw Error("Manifesto indisponível.");
    const manifest = verifyManifest(
      await response.json(),
      this.publicKey,
      release,
    );
    this.expectedHash = manifest.sha256;
    mkdirSync(this.directory, { recursive: true });
    const path = join(this.directory, manifest.name);
    if (
      existsSync(path) &&
      createHash("sha256").update(readFileSync(path)).digest("hex") ===
        manifest.sha256
    ) {
      this.downloaded = path;
      return path;
    }
    const download = await fetch(manifest.url, {
      signal: AbortSignal.timeout(180000),
    });
    if (!download.ok || !download.body) throw Error("Download indisponível.");
    const chunks: Buffer[] = [];
    let size = 0;
    for await (const chunk of download.body as any) {
      size += chunk.length;
      if (size > manifest.size) throw Error("Tamanho da atualização inválido.");
      chunks.push(Buffer.from(chunk));
    }
    const buffer = Buffer.concat(chunks);
    if (
      size !== manifest.size ||
      createHash("sha256").update(buffer).digest("hex") !== manifest.sha256
    )
      throw Error("Integridade da atualização inválida.");
    writeFileSync(path, buffer, { mode: 0o600 });
    this.downloaded = path;
    return path;
  }
}
