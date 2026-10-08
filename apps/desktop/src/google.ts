import { initializeApp } from "firebase/app";
import {
  getAuth,
  GoogleAuthProvider,
  signInWithPopup,
  inMemoryPersistence,
  setPersistence,
  signOut,
} from "firebase/auth";
const button = document.getElementById("login") as HTMLButtonElement;
const message = document.getElementById("status")!;
const config = await fetch("/config").then((r) => r.json());
const auth = getAuth(initializeApp(config));
await setPersistence(auth, inMemoryPersistence);
button.onclick = async () => {
  button.disabled = true;
  message.textContent = "Conectando à sua conta…";
  try {
    const result = await signInWithPopup(auth, new GoogleAuthProvider());
    const credential = GoogleAuthProvider.credentialFromResult(result);
    if (!credential?.idToken)
      throw Error("O Google não retornou a credencial.");
    const nonce = new URLSearchParams(location.hash.slice(1)).get("session");
    const response = await fetch("/complete", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ nonce, idToken: credential.idToken }),
    });
    if (!response.ok) throw Error("Não foi possível conectar o aplicativo.");
    await signOut(auth);
    location.hash = "";
    message.textContent = "Conta conectada. Você já pode voltar ao Veyra Life.";
    button.hidden = true;
  } catch (e) {
    const code = (e as { code?: string }).code;
    message.textContent =
      code === "auth/unauthorized-domain"
        ? "Este computador ainda precisa do domínio localhost autorizado no Firebase."
        : "Não foi possível entrar. Confira a conta ou tente novamente.";
    button.disabled = false;
  }
};
