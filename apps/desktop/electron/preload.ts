import { contextBridge, ipcRenderer, webUtils } from "electron";
import type { DesktopAPI } from "../shared/model";
let scopeUid: string | null | undefined;
ipcRenderer.on("veyra:invalidate", () => (scopeUid = undefined));
const api: DesktopAPI = {
  call: async (method, value) => {
    const result = await ipcRenderer.invoke(
      "veyra:call",
      method,
      value,
      scopeUid,
    );
    if (method === "snapshot") scopeUid = result.uid;
    return result;
  },
  onChange: (listener) => {
    const handler = () => listener();
    ipcRenderer.on("veyra:changed", handler);
    return () => ipcRenderer.removeListener("veyra:changed", handler);
  },
  onCommand: (listener) => {
    const handler = (_event: any, command: string) => listener(command);
    ipcRenderer.on("veyra:command", handler);
    return () => ipcRenderer.removeListener("veyra:command", handler);
  },
  filePath: (file) => {
    const path = webUtils.getPathForFile(file);
    if (path) ipcRenderer.send("veyra:drop", path, scopeUid);
    return path;
  },
};
contextBridge.exposeInMainWorld("veyra", api);
