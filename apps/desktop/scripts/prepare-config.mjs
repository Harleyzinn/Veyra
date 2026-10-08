import { writeFile, mkdir } from "node:fs/promises";
import { existsSync } from "node:fs";
import { generateKeyPairSync } from "node:crypto";
await mkdir("resources", { recursive: true });
if (!existsSync("resources/firebase.json"))
  throw Error(
    "Obtenha a configuração do aplicativo WEB existente com Firebase CLI apps:sdkconfig WEB e salve em resources/firebase.json. A configuração Android não serve para o login desktop.",
  );
const privatePath = "../../.signing/desktop-update-private.pem";
if (!existsSync(privatePath)) {
  const keys = generateKeyPairSync("ed25519");
  await writeFile(
    privatePath,
    keys.privateKey.export({ type: "pkcs8", format: "pem" }),
    { mode: 0o600 },
  );
  await writeFile(
    "resources/update-public.pem",
    keys.publicKey.export({ type: "spki", format: "pem" }),
  );
}
console.log(
  "Configuração do projeto existente e chave pública de atualização preparadas. Nenhuma credencial privada foi exibida.",
);
