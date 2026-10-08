import React, { useEffect, useState } from "react";
import {
  Cloud,
  LogOut,
  Download,
  Upload,
  ShieldCheck,
  Keyboard,
  Monitor,
  Palette,
  Bell,
  Clipboard,
  RefreshCw,
  ArrowRight,
  Computer,
  HardDrive,
  Plus,
} from "lucide-react";
import { Snapshot } from "../shared/model";
import { api, Panel, Button, Field, Modal, Empty, dateLabel } from "./ui";
const tabItems = [
  ["Conta", Cloud],
  ["Dados", HardDrive],
  ["Privacidade", ShieldCheck],
  ["Aparência", Palette],
  ["Atalhos", Keyboard],
  ["Windows", Monitor],
  ["Dispositivos", Computer],
  ["Atualizações", Download],
] as const;
export function Settings({
  data,
  onLogin,
}: {
  data: Snapshot;
  onLogin: () => void;
}) {
  const [backupPassword, setBackupPassword] = useState("");
  const [tab, setTab] = useState("Conta");
  const [conflicts, setConflicts] = useState<any[]>([]);
  const [preview, setPreview] = useState<any>(null);
  const [replace, setReplace] = useState(false);
  const [release, setRelease] = useState<any>(null);
  const [downloaded, setDownloaded] = useState(false);
  const [updateState, setUpdateState] = useState("");
  const [shortcutDraft, setShortcutDraft] = useState({
    capture: "CommandOrControl+Shift+Space",
    task: "CommandOrControl+Alt+T",
    expense: "CommandOrControl+Alt+G",
    note: "CommandOrControl+Alt+N",
    focus: "CommandOrControl+Alt+F",
    recover: "CommandOrControl+Shift+R",
    ...data.desktop.shortcuts,
  });
  const [shortcutResult, setShortcutResult] = useState("");
  useEffect(() => {
    if (tab === "Dados") void api("conflicts").then(setConflicts);
  }, [tab, data.sync.conflicts]);
  const setDesktop = (key: string, value: any) =>
    void api("desktop", { [key]: value });
  async function checkUpdates() {
    setUpdateState("Consultando…");
    try {
      const r = await api("updatesCheck");
      setRelease(r);
      setUpdateState(
        r ? "Nova versão disponível." : "Você está na versão mais recente.",
      );
    } catch (e) {
      setUpdateState((e as Error).message);
    }
  }
  return (
    <>
      <div className="page-title">
        <div>
          <div className="eyebrow">DO SEU JEITO, SOB SEU CONTROLE</div>
          <h1>Configurações</h1>
          <p>Conta, privacidade e um espaço com a sua cara.</p>
        </div>
        <span className="version-chip">Desktop {data.version}</span>
      </div>
      <div className="settings-layout">
        <aside>
          {tabItems.map(([name, Icon]) => (
            <button
              key={name}
              className={tab === name ? "active" : ""}
              onClick={() => setTab(name)}
            >
              <Icon size={17} />
              {name}
            </button>
          ))}
        </aside>
        <div>
          {tab === "Conta" && (
            <>
              <Panel
                title="Veyra Account"
                subtitle="A mesma identidade no computador e no celular"
              >
                {data.user ? (
                  <>
                    <div className="account-profile">
                      <div className="avatar large">
                        {data.user.name.slice(0, 1).toUpperCase()}
                      </div>
                      <div>
                        <h3>{data.user.name}</h3>
                        <p>{data.user.email}</p>
                        <span
                          className={
                            "badge " + (data.user.verified ? "mint" : "coral")
                          }
                        >
                          {data.user.verified
                            ? "E-mail verificado"
                            : "Confirme seu e-mail para sincronizar"}
                        </span>
                      </div>
                      <Button onClick={() => void api("logout")}>
                        <LogOut size={15} />
                        Sair da conta
                      </Button>
                    </div>
                    {!data.user.verified && (
                      <Button onClick={() => void api("refreshUser")}>
                        Já confirmei o e-mail
                      </Button>
                    )}
                    <div className="sync-stats">
                      <div>
                        <strong>{data.sync.pending}</strong>
                        <span>Alterações pendentes</span>
                      </div>
                      <div>
                        <strong>{data.sync.conflicts}</strong>
                        <span>Conflitos para revisar</span>
                      </div>
                      <div>
                        <strong>
                          {data.sync.lastSync
                            ? new Date(data.sync.lastSync).toLocaleTimeString(
                                "pt-BR",
                                { hour: "2-digit", minute: "2-digit" },
                              )
                            : "—"}
                        </strong>
                        <span>Última sincronização</span>
                      </div>
                    </div>
                    {data.sync.error && (
                      <p className="error">{data.sync.error}</p>
                    )}
                    <Button onClick={() => void api("sync")}>
                      <RefreshCw size={15} />
                      Sincronizar agora
                    </Button>
                  </>
                ) : (
                  <>
                    <Empty
                      title="Seu Veyra, em todos os lugares"
                      detail="Entre com a conta Google usada no celular. O espaço visitante será preservado separadamente."
                    />
                    <Button kind="primary" onClick={onLogin}>
                      <Cloud size={16} />
                      Conectar conta
                    </Button>
                  </>
                )}
              </Panel>
              <Panel title="O que acompanha você">
                <p>
                  Finanças, tarefas, notas, eventos, hábitos, metas,
                  preferências e os demais registros usam o mesmo UID do
                  Firebase. O desktop respeita as revisões e os caminhos já
                  usados pelo Android.
                </p>
                <p className="muted">
                  Arquivos anexados permanecem no aparelho original. O plano
                  gratuito não inclui armazenamento de anexos na nuvem.
                </p>
                <p className="muted">
                  Espaços compartilhados poderão ser adicionados no futuro com
                  permissões próprias. Seus dados atuais são privados.
                </p>
              </Panel>
            </>
          )}
          {tab === "Dados" && (
            <>
              <Panel
                title="Central de backup"
                subtitle="Leve seus dados com você, sem substituições silenciosas"
              >
                <Field
                  label="Senha do backup • opcional para exportar"
                  type="password"
                  value={backupPassword}
                  onChange={setBackupPassword}
                  placeholder="Use a senha do arquivo para importar"
                />
                <div className="actions">
                  <Button
                    kind="primary"
                    onClick={() =>
                      void api("backup", { password: backupPassword })
                    }
                  >
                    <Download size={16} />
                    Exportar backup completo
                  </Button>
                  <Button
                    onClick={() =>
                      void api("importPreview", {
                        password: backupPassword,
                      }).then(setPreview)
                    }
                  >
                    <Upload size={16} />
                    Importar backup
                  </Button>
                </div>
                <p className="muted">
                  O backup é compatível com o celular; uma senha protege o
                  arquivo com criptografia. O backup inclui anexos disponíveis
                  neste PC. O arquivo exportado pode conter dados privados;
                  guarde-o em um local protegido.
                </p>
                <p className="muted">
                  Último backup:{" "}
                  {data.desktop.lastBackup
                    ? new Date(data.desktop.lastBackup).toLocaleString("pt-BR")
                    : "Ainda não exportado neste PC"}
                </p>
                <p>
                  {data.items.length} registros carregados · cache criptografado
                  no Windows •{" "}
                  {(Number(data.desktop.storageBytes || 0) / 1048576).toFixed(
                    2,
                  )}{" "}
                  MB nesta conta
                </p>
              </Panel>
              <Panel
                title="Conflitos de sincronização"
                subtitle="As duas versões ficam preservadas no histórico"
              >
                {conflicts.map((c) => (
                  <div className="conflict" key={c.id}>
                    <h3>{c.local.title}</h3>
                    <div className="split">
                      <div>
                        <small>NESTE PC</small>
                        <p>{c.local.title}</p>
                        <pre>{c.local.notes.slice(0, 500)}</pre>
                        <p>{dateLabel(c.local.date)}</p>
                        <Button
                          onClick={() =>
                            void api("resolve", { id: c.id, useRemote: false })
                          }
                        >
                          Manter minha versão
                        </Button>
                      </div>
                      <div>
                        <small>NA NUVEM • REVISÃO {c.remote.revision}</small>
                        <p>{c.remote.item.title}</p>
                        <pre>{c.remote.item.notes.slice(0, 500)}</pre>
                        <p>{dateLabel(c.remote.item.date)}</p>
                        <Button
                          onClick={() =>
                            void api("resolve", { id: c.id, useRemote: true })
                          }
                        >
                          Usar versão da nuvem
                        </Button>
                      </div>
                    </div>
                  </div>
                ))}
                {!conflicts.length && (
                  <Empty
                    title="Tudo em acordo"
                    detail="Se duas edições simultâneas divergirem, as versões aparecerão aqui."
                  />
                )}
              </Panel>
              <Trash />
            </>
          )}
          {tab === "Privacidade" && (
            <>
              <Panel title="Você decide o que entra e sai">
                <div className="privacy-row">
                  <ShieldCheck />
                  <div>
                    <h3>Armazenamento protegido</h3>
                    <p>
                      SQLite local criptografado com AES-256-GCM. A chave é
                      protegida pelo Windows na sua sessão de usuário. Senhas e
                      tokens nunca aparecem na interface ou nos backups.
                    </p>
                  </div>
                </div>
                <div className="privacy-row">
                  <Cloud />
                  <div>
                    <h3>Sincronização privada</h3>
                    <p>
                      Somente os registros da sua conta são enviados ao Firebase
                      existente. Sem telemetria ou analytics. Valores
                      financeiros não são enviados a serviços de IA.
                    </p>
                  </div>
                </div>
                <div className="privacy-row">
                  <Clipboard />
                  <div>
                    <h3>Área de transferência</h3>
                    <p>
                      Opcional. O app lê o texto apenas quando você usa
                      “Capturar texto copiado”. Você revisa e confirma antes de
                      criar um registro.
                    </p>
                    <Toggle
                      label="Permitir leitura local do clipboard"
                      checked={!!data.desktop.clipboard}
                      change={(v) => setDesktop("clipboard", v)}
                    />
                  </div>
                </div>
                <div className="privacy-row">
                  <Cloud />
                  <div>
                    <h3>Previsão online</h3>
                    <p>
                      A busca de cidades e a atualização enviam a localização
                      escolhida ao Open-Meteo. O cache continua disponível
                      quando a atualização estiver desativada.
                    </p>
                    <Toggle
                      label="Permitir atualização do clima neste PC"
                      checked={!!data.desktop.weatherEnabled}
                      change={(v) => setDesktop("weatherEnabled", v)}
                    />
                  </div>
                </div>
                <div className="privacy-row">
                  <HardDrive />
                  <div>
                    <h3>Arquivos</h3>
                    <p>
                      Selecionados ou arrastados por você, com confirmação.
                      Permanecem locais. Nenhum upload de arquivos está
                      habilitado.
                    </p>
                  </div>
                </div>
                <div className="privacy-row">
                  <Bell />
                  <div>
                    <h3>Notificações</h3>
                    <p>
                      Podem mostrar títulos de registros na tela do Windows.
                      Ative apenas se desejar esses lembretes.
                    </p>
                    <Toggle
                      label="Permitir notificações do Veyra"
                      checked={!!data.desktop.notifications}
                      change={(v) => setDesktop("notifications", v)}
                    />
                  </div>
                </div>
              </Panel>
            </>
          )}
          {tab === "Aparência" && (
            <Panel
              title="Veyra Design System"
              subtitle="Mais espaço para trabalhar, do seu jeito"
            >
              <div className="form-grid">
                <Field
                  label="Tema"
                  value={
                    data.desktop.theme || data.preferences.theme || "system"
                  }
                  onChange={(v) => setDesktop("theme", v)}
                  options={[
                    { value: "system", label: "Sistema" },
                    { value: "dark", label: "Escuro" },
                    { value: "light", label: "Claro" },
                  ]}
                />
                <Field
                  label="Densidade"
                  value={data.desktop.density || "comfortable"}
                  onChange={(v) => setDesktop("density", v)}
                  options={[
                    { value: "comfortable", label: "Confortável" },
                    { value: "compact", label: "Compacta" },
                  ]}
                />
                <Field
                  label="Escala"
                  value={String(data.desktop.scale || 100)}
                  onChange={(v) => setDesktop("scale", Number(v))}
                  options={[90, 100, 110, 125, 150].map((n) => ({
                    value: String(n),
                    label: n + "%",
                  }))}
                />
                <Field
                  label="Destaque"
                  value={data.desktop.accent || "violet"}
                  onChange={(v) => setDesktop("accent", v)}
                  options={[
                    ["violet", "Violeta"],
                    ["mint", "Menta"],
                    ["blue", "Azul"],
                    ["amber", "Âmbar"],
                  ].map(([value, label]) => ({ value, label }))}
                />
              </div>
              <p className="muted">
                A interface acompanha a preferência de movimento reduzido do
                Windows. Widgets e layout são ajustados na central inicial.
              </p>
            </Panel>
          )}
          {tab === "Atalhos" && (
            <Panel
              title="Atalhos globais"
              subtitle="Funcionam mesmo com o Veyra na bandeja"
            >
              <div className="form-grid">
                {Object.entries(shortcutDraft).map(([name, key]) => (
                  <Field
                    key={name}
                    label={
                      {
                        capture: "Captura rápida",
                        task: "Nova tarefa",
                        expense: "Novo gasto",
                        note: "Nova nota",
                        focus: "Foco",
                        recover: "Recuperar painéis",
                      }[name] || name
                    }
                    value={String(key)}
                    onChange={(v) =>
                      setShortcutDraft((s: any) => ({ ...s, [name]: v }))
                    }
                  />
                ))}
              </div>
              <p className="muted">
                Use combinações como CommandOrControl+Shift+Space. Atalhos
                ocupados são rejeitados sem apagar os anteriores.
              </p>
              <Button
                kind="primary"
                onClick={() =>
                  void api("shortcuts", shortcutDraft)
                    .then(() => setShortcutResult("Atalhos salvos."))
                    .catch((e) => setShortcutResult(e.message))
                }
              >
                Salvar atalhos
              </Button>
              {shortcutResult && <p role="status">{shortcutResult}</p>}
              <hr />
              <p>
                <kbd>Ctrl</kbd> + <kbd>K</kbd> Pesquisa e comandos
              </p>
              <p>
                <kbd>Esc</kbd> Fechar diálogo
              </p>
              <p>
                <kbd>Tab</kbd> Navegar pelos controles
              </p>
            </Panel>
          )}
          {tab === "Windows" && (
            <>
              <Panel title="Um companheiro discreto">
                <Toggle
                  label="Iniciar com o Windows"
                  checked={!!data.desktop.startWindows}
                  change={(v) => void api("startup", v)}
                />
                <Toggle
                  label="Iniciar minimizado"
                  checked={!!data.desktop.startMinimized}
                  change={(v) => setDesktop("startMinimized", v)}
                />
                <Toggle
                  label="Abrir Meu Dia ao iniciar"
                  checked={!!data.desktop.openDay}
                  change={(v) => setDesktop("openDay", v)}
                />
                <Toggle
                  label="Manter na bandeja ao fechar a janela"
                  checked={data.desktop.keepTray !== false}
                  change={(v) => setDesktop("keepTray", v)}
                />
                <Toggle
                  label="Notificações de lembretes"
                  checked={!!data.desktop.notifications}
                  change={(v) => setDesktop("notifications", v)}
                />
              </Panel>
              <Panel title="Janelas que acompanham seu trabalho">
                <div className="widget-options">
                  {[
                    ["mini", "Mini dashboard"],
                    ["dock", "Veyra Dock"],
                    ["widget-clock", "Hora e data"],
                    ["widget-timer", "Cronômetro"],
                    ["widget-notes", "Notas"],
                    ["widget-finance", "Finanças"],
                    ["widget-tasks", "Tarefas"],
                    ["widget-weather", "Clima"],
                    ["widget-calendar", "Calendário"],
                    ["widget-habits", "Hábitos"],
                    ["widget-focus", "Foco"],
                  ].map(([role, title]) => (
                    <Button
                      key={role}
                      onClick={() => void api("openWindow", role)}
                    >
                      <Plus size={15} />
                      {title}
                    </Button>
                  ))}
                </div>
                <Toggle
                  label="Reduzir efeitos visuais dos painéis"
                  checked={!!data.desktop.reducedEffects}
                  change={(v) => setDesktop("reducedEffects", v)}
                />
                <div className="actions">
                  <Button onClick={() => void api("panelsAll", "hide")}>
                    Ocultar todos
                  </Button>
                  <Button onClick={() => void api("panelsAll", "show")}>
                    Mostrar todos
                  </Button>
                  <Button onClick={() => void api("panelsAll", "recover")}>
                    Recuperar painéis
                  </Button>
                </div>
                <p className="muted">
                  Mini dashboard e widgets podem ficar acima das outras janelas.
                  Posição e tamanho são lembrados e corrigidos se um monitor for
                  removido.
                </p>
              </Panel>
            </>
          )}
          {tab === "Dispositivos" && (
            <Panel
              title="Meus dispositivos"
              subtitle="Registros de presença da sua conta"
            >
              <div className="device-card">
                <Monitor size={28} />
                <div>
                  <h3>Este PC</h3>
                  <p>Windows · cache privado</p>
                  <small>Identificador: {data.deviceId.slice(0, 8)}</small>
                </div>
                <span className="badge mint">Atual</span>
              </div>
              {data.items
                .filter(
                  (i) =>
                    i.type === "device" && i.fields.deviceId !== data.deviceId,
                )
                .map((i) => (
                  <div className="device-card" key={i.id}>
                    <Computer size={26} />
                    <div>
                      <h3>{i.title}</h3>
                      <p>{i.fields.platform || "Dispositivo"}</p>
                      <small>
                        Último acesso:{" "}
                        {i.fields.lastSeen
                          ? new Date(Number(i.fields.lastSeen)).toLocaleString(
                              "pt-BR",
                            )
                          : "Não informado"}
                      </small>
                    </div>
                  </div>
                ))}
              <p className="muted">
                Encerrar sessões remotas exige uma infraestrutura de revogação
                confiável. Esta versão não apresenta um botão que apenas simula
                revogação. Seus dados não são compartilhados com outros UIDs.
              </p>
            </Panel>
          )}
          {tab === "Atualizações" && (
            <Panel
              title="Sempre em evolução"
              subtitle={"Versão instalada: " + data.version}
            >
              <Button kind="primary" onClick={() => void checkUpdates()}>
                <RefreshCw size={16} />
                Buscar atualizações
              </Button>
              {updateState && <p role="status">{updateState}</p>}
              {release && (
                <>
                  <h3>Veyra Life {release.version}</h3>
                  <pre className="release-notes">{release.notes}</pre>
                  <Button
                    onClick={() => {
                      setUpdateState("Baixando e verificando assinatura…");
                      void api("updatesDownload")
                        .then(() => {
                          setDownloaded(true);
                          setUpdateState(
                            "Download verificado. Pronto para instalar.",
                          );
                        })
                        .catch((e) => setUpdateState(e.message));
                    }}
                    disabled={downloaded}
                  >
                    <Download size={16} />
                    Baixar atualização verificada
                  </Button>
                  {downloaded && (
                    <Button
                      kind="primary"
                      onClick={() => void api("updatesInstall")}
                    >
                      Reiniciar e atualizar
                      <ArrowRight size={16} />
                    </Button>
                  )}
                </>
              )}
              <p className="muted">
                As atualizações do desktop usam manifesto assinado com Ed25519 e
                conferência SHA-256 do instalador. O app só executa o arquivo
                após sua confirmação.
              </p>
            </Panel>
          )}
        </div>
      </div>
      {preview && (
        <Modal title="Prévia do backup" onClose={() => setPreview(null)}>
          <div className="form-body">
            <p>
              {preview.total} registros · {preview.newCount} novos ·{" "}
              {preview.changes.length} diferentes
            </p>
            <p className="muted">
              A importação não muda sua conta nem traz tokens. Versões
              substituídas ficam no histórico local.
            </p>
            <Toggle
              label="Substituir registros diferentes pelos do backup"
              checked={replace}
              change={setReplace}
            />
            {preview.changes.slice(0, 15).map((i: any) => (
              <p key={i.id}>{i.title}</p>
            ))}
            <Button
              kind="primary"
              onClick={() =>
                void api("importConfirm", { replace }).then((ok) => {
                  if (ok) setPreview(null);
                })
              }
            >
              Confirmar importação
            </Button>
          </div>
        </Modal>
      )}
    </>
  );
}
export function Toggle({
  label,
  checked,
  change,
}: {
  label: string;
  checked: boolean;
  change: (v: boolean) => void;
}) {
  return (
    <label className="setting-toggle">
      <span>{label}</span>
      <input
        type="checkbox"
        checked={checked}
        onChange={(e) => change(e.target.checked)}
      />
      <i aria-hidden="true" />
    </label>
  );
}
function Trash() {
  const [items, setItems] = useState<any[]>([]);
  const [shown, setShown] = useState(false);
  return (
    <Panel
      title="Lixeira"
      subtitle="Registros importantes usam exclusão reversível"
    >
      <Button
        onClick={() => {
          setShown(!shown);
          void api("search", { deleted: true, limit: 100 }).then((r) =>
            setItems(r.items),
          );
        }}
      >
        {shown ? "Fechar lixeira" : "Ver registros excluídos"}
      </Button>
      {shown &&
        items.map((i) => (
          <div className="invoice-row" key={i.id}>
            <span>{i.title}</span>
            <Button
              onClick={() =>
                void api("restore", i.id).then(() =>
                  setItems((v) => v.filter((r) => r.id !== i.id)),
                )
              }
            >
              Restaurar
            </Button>
          </div>
        ))}
      {shown && !items.length && <Empty title="A lixeira está vazia" />}
    </Panel>
  );
}
