import { createServer, Server } from "node:http";
import { readFileSync } from "node:fs";
import { join } from "node:path";
import { randomBytes, timingSafeEqual } from "node:crypto";
import { FirebaseConfig } from "./firebase";
export function systemGoogle(
  config: FirebaseConfig,
  uiDir: string,
  open: (url: string) => Promise<void>,
): { result: Promise<string>; cancel: () => void } {
  let server: Server;
  let rejectResult: (error: Error) => void;
  let timer: ReturnType<typeof setTimeout>;
  const nonce = randomBytes(32).toString("hex");
  let done = false;
  const result = new Promise<string>((resolve, reject) => {
    rejectResult = reject;
    server = createServer(async (req, res) => {
      const address = server.address();
      if (!address || typeof address === "string") return;
      const origin = `http://localhost:${address.port}`;
      if (req.headers.host !== `localhost:${address.port}`) {
        res.writeHead(403).end();
        return;
      }
      res.setHeader("Cache-Control", "no-store");
      res.setHeader("Referrer-Policy", "no-referrer");
      res.setHeader("X-Content-Type-Options", "nosniff");
      res.setHeader(
        "Content-Security-Policy",
        "default-src 'self'; script-src 'self' https://apis.google.com; style-src 'self' 'unsafe-inline'; connect-src 'self' https://*.googleapis.com https://*.firebaseapp.com; frame-src https://*.firebaseapp.com https://accounts.google.com; img-src 'self' https://*.googleusercontent.com; object-src 'none'; base-uri 'none'",
      );
      if (req.method === "GET" && req.url === "/") {
        res.setHeader("Content-Type", "text/html; charset=utf-8");
        res.end(readFileSync(join(uiDir, "google.html")));
      } else if (req.method === "GET" && req.url === "/google.js") {
        res.setHeader("Content-Type", "text/javascript");
        res.end(readFileSync(join(uiDir, "google.js")));
      } else if (req.method === "GET" && req.url === "/config") {
        res.setHeader("Content-Type", "application/json");
        res.end(JSON.stringify(config));
      } else if (
        req.method === "POST" &&
        req.url === "/complete" &&
        req.headers.origin === origin &&
        req.headers["content-type"] === "application/json"
      ) {
        let body = "";
        try {
          for await (const chunk of req) {
            body += chunk;
            if (body.length > 20000) throw Error("Resposta inválida.");
          }
          const data = JSON.parse(body);
          if (
            done ||
            typeof data.nonce !== "string" ||
            data.nonce.length !== nonce.length ||
            !timingSafeEqual(Buffer.from(data.nonce), Buffer.from(nonce)) ||
            typeof data.idToken !== "string" ||
            data.idToken.length > 15000
          ) {
            res.writeHead(403).end();
            return;
          }
          done = true;
          res.end("{}");
          clearTimeout(timer);
          resolve(data.idToken);
          server.close();
        } catch {
          res.writeHead(400).end();
        }
      } else res.writeHead(404).end();
    });
    server.on("error", () =>
      reject(Error("Não foi possível abrir o login seguro.")),
    );
    server.listen(0, "127.0.0.1", () => {
      const address = server.address();
      if (address && typeof address !== "string")
        void open(`http://localhost:${address.port}/#session=${nonce}`).catch(
          reject,
        );
    });
    timer = setTimeout(() => {
      server.close();
      reject(Error("O login expirou. Tente novamente."));
    }, 180000);
  });
  return {
    result,
    cancel: () => {
      clearTimeout(timer);
      server?.close();
      rejectResult?.(Error("Login cancelado."));
    },
  };
}
