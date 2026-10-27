import {
  useCallback,
  useEffect,
  useRef,
  useState,
  useSyncExternalStore,
  type FormEvent,
  type ReactNode,
} from "react";
import {
  ArrowDownLeft,
  ArrowRight,
  ArrowUpRight,
  Check,
  ChevronRight,
  CircleHelp,
  Clock3,
  History,
  LayoutDashboard,
  Plus,
  RefreshCw,
  Search,
  ShieldCheck,
  Users,
  Wallet,
  X,
} from "lucide-react";
import { Commands, snapshot } from "./api";
import {
  currency,
  dateTime,
  fullName,
  moneyInput,
  type Account,
  type Snapshot,
} from "./types";

const storage = {
  getItem: (key: string) => window.sessionStorage.getItem(key),
  setItem: (key: string, value: string) =>
    window.sessionStorage.setItem(key, value),
  removeItem: (key: string) => window.sessionStorage.removeItem(key),
};
function Modal({
  title,
  children,
  onClose,
}: {
  title: string;
  children: ReactNode;
  onClose: () => void;
}) {
  const ref = useRef<HTMLDialogElement>(null);
  const close = useRef(onClose);
  close.current = onClose;
  useEffect(() => {
    const element = ref.current!;
    element.showModal();
    const cancel = (event: Event) => {
      event.preventDefault();
      close.current();
    };
    element.addEventListener("cancel", cancel);
    return () => {
      element.removeEventListener("cancel", cancel);
      element.close();
    };
  }, []);
  return (
    <dialog ref={ref} aria-labelledby="dialog-title">
      <header className="dialog-heading">
        <h2 id="dialog-title">{title}</h2>
        <button
          className="icon-button"
          aria-label="Close dialog"
          onClick={onClose}
        >
          <X size={20} />
        </button>
      </header>
      {children}
    </dialog>
  );
}
function Avatar({
  account,
  small = false,
}: {
  account: Account;
  small?: boolean;
}) {
  return (
    <span className={`avatar ${small ? "small" : ""}`} aria-hidden="true">
      {account.firstName[0]}
      {account.lastName[0]}
    </span>
  );
}
const empty: Snapshot = { accounts: [], queue: [], entries: [], open: false };

export function App() {
  const [commands] = useState(() => new Commands(storage));
  const command = useSyncExternalStore(
    commands.subscribe,
    commands.getSnapshot,
  );
  const [data, setData] = useState<Snapshot>(empty);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [connectionError, setConnectionError] = useState("");
  const [updated, setUpdated] = useState<Date | null>(null);
  const [view, setView] = useState<"overview" | "directory" | "activity">(
    "overview",
  );
  const [query, setQuery] = useState("");
  const [teller, setTeller] = useState("teller-1");
  const [activeId, setActiveId] = useState<number | null>(null);
  const [operation, setOperation] = useState<"deposits" | "withdrawals">(
    "deposits",
  );
  const [amount, setAmount] = useState("");
  const [formError, setFormError] = useState("");
  const [dialog, setDialog] = useState<
    "check-in" | "close-account" | "branch" | "help" | null
  >(null);
  const [closureTarget, setClosureTarget] = useState<{
    ticketId: number;
    accountId: string;
    name: string;
  } | null>(null);
  const [branchTarget, setBranchTarget] = useState(false);
  const [profileId, setProfileId] = useState<string | null>(null);
  const [checkAccount, setCheckAccount] = useState("DEMO-1001");
  const [firstName, setFirstName] = useState("");
  const [lastName, setLastName] = useState("");
  const request = useRef<AbortController | null>(null);
  const mounted = useRef(true);
  const refresh = useCallback(async () => {
    request.current?.abort();
    const controller = new AbortController();
    request.current = controller;
    setRefreshing(true);
    try {
      const result = await snapshot(controller.signal);
      if (!controller.signal.aborted && mounted.current) {
        setData(result);
        setConnectionError("");
        setUpdated(new Date());
      }
    } catch {
      if (!controller.signal.aborted && mounted.current)
        setConnectionError(
          "Unable to refresh the branch. Actions are paused until the connection is restored.",
        );
    } finally {
      if (!controller.signal.aborted && mounted.current) {
        setLoading(false);
        setRefreshing(false);
      }
    }
  }, []);
  useEffect(() => {
    mounted.current = true;
    void refresh();
    const interval = window.setInterval(() => {
      if (document.visibilityState === "visible") void refresh();
    }, 10000);
    const focus = () => {
      void refresh();
    };
    window.addEventListener("focus", focus);
    return () => {
      mounted.current = false;
      request.current?.abort();
      clearInterval(interval);
      window.removeEventListener("focus", focus);
    };
  }, [refresh]);
  const waiting = data.queue.filter((t) => t.status === "WAITING");
  const serving = data.queue.filter((t) => t.status === "SERVING");
  const active =
    serving.find((t) => t.id === activeId) ??
    serving.find((t) => t.teller === teller.trim().toLowerCase()) ??
    serving[0];
  const account = data.accounts.find((a) => a.id === active?.accountId);
  const profile = data.accounts.find((a) => a.id === profileId);
  const closureAccount = data.accounts.find(
    (a) => a.id === closureTarget?.accountId,
  );
  useEffect(() => {
    setAmount("");
    setFormError("");
  }, [active?.id]);
  const showCheckIn = () => {
    setFirstName("");
    setLastName("");
    setCheckAccount(data.accounts.find((a) => a.status === "OPEN")?.id ?? "");
    setDialog("check-in");
  };
  const filtered = data.accounts.filter((a) =>
    `${fullName(a)} ${a.id}`.toLowerCase().includes(query.toLowerCase().trim()),
  );
  const entries = data.entries.filter((e) => {
    const owner = data.accounts.find((a) => a.id === e.accountId);
    return `${e.accountId} ${owner ? fullName(owner) : ""} ${e.kind}`
      .toLowerCase()
      .includes(query.toLowerCase().trim());
  });
  const locked =
    command.storageBlocked ||
    loading ||
    Boolean(connectionError) ||
    command.busy ||
    Boolean(command.pending);
  const workLocked = locked || !data.open;
  const run = async (path: string, body: unknown, label: string) => {
    const success = await commands.start(path, body, label);
    if (success) {
      setDialog(null);
      setAmount("");
      setFormError("");
    } else if (commands.getSnapshot().pending) setDialog(null);
    await refresh();
    return success;
  };
  const title =
    view === "overview"
      ? "Branch control."
      : view === "directory"
        ? "Client accounts."
        : "Transaction ledger.";
  const switchView = (next: typeof view) => {
    setView(next);
    setQuery("");
  };
  const submitMoney = (event: FormEvent) => {
    event.preventDefault();
    const normalized = moneyInput(amount);
    if (!normalized) {
      setFormError(
        "Enter a positive amount with no more than two decimal places.",
      );
      return;
    }
    if (!active || !account || workLocked) return;
    setFormError("");
    void run(
      `/tickets/${active.id}/${operation}`,
      { amount: normalized },
      `${operation === "deposits" ? "Deposit" : "Withdrawal"} of $${normalized} for ${fullName(account)}`,
    );
  };
  const historyTable = (records = entries) => (
    <div className="table-scroll">
      <table>
        <thead>
          <tr>
            <th>Transaction</th>
            <th>Client</th>
            <th>Date & time</th>
            <th className="numeric">Amount</th>
            <th className="numeric">Balance after</th>
          </tr>
        </thead>
        <tbody>
          {records.map((e) => {
            const owner = data.accounts.find((a) => a.id === e.accountId);
            return (
              <tr key={e.id}>
                <td>
                  <span
                    className={`transaction-icon ${e.kind === "WITHDRAWAL" ? "out" : ""}`}
                  >
                    {e.kind === "WITHDRAWAL" ? (
                      <ArrowUpRight size={17} />
                    ) : e.kind === "DEPOSIT" ? (
                      <ArrowDownLeft size={17} />
                    ) : (
                      <Check size={17} />
                    )}
                  </span>
                  <strong>
                    {e.kind === "CLOSE_ACCOUNT"
                      ? "Account closed"
                      : e.kind === "DEPOSIT"
                        ? "Deposit"
                        : "Withdrawal"}
                  </strong>
                  <small className="row-secondary">
                    #{e.id} · Ticket {e.ticketId}
                  </small>
                </td>
                <td>
                  {owner ? fullName(owner) : e.accountId}
                  <small className="row-secondary">{e.accountId}</small>
                </td>
                <td>{dateTime(e.createdAt)}</td>
                <td
                  className={`numeric ${e.kind === "DEPOSIT" ? "positive" : ""}`}
                >
                  {e.kind === "CLOSE_ACCOUNT"
                    ? "—"
                    : `${e.kind === "DEPOSIT" ? "+" : "−"}${currency(e.amount)}`}
                </td>
                <td className="numeric">{currency(e.balanceAfter)}</td>
              </tr>
            );
          })}
        </tbody>
      </table>
      {records.length === 0 && (
        <div className="table-empty">
          <History size={26} />
          <h3>No transactions yet</h3>
          <p>
            {query
              ? "Try a different search."
              : "Completed deposits, withdrawals, and account closures will appear here."}
          </p>
        </div>
      )}
    </div>
  );

  return (
    <div className="app-shell">
      <a className="skip-link" href="#main">
        Skip to workspace
      </a>
      <aside className="sidebar">
        <a
          href="#"
          className="brand"
          onClick={(e) => {
            e.preventDefault();
            switchView("overview");
          }}
          aria-label="Banker overview"
        >
          <span className="brand-mark">
            b<span>·</span>
          </span>
          <span>
            banker<span className="brand-caption">TELLER WORKSPACE</span>
          </span>
        </a>
        <div className="nav-label">WORKSPACE</div>
        <nav aria-label="Main navigation">
          <button
            className={view === "overview" ? "active" : ""}
            onClick={() => switchView("overview")}
            aria-current={view === "overview" ? "page" : undefined}
          >
            <LayoutDashboard size={19} />
            Overview
            <ChevronRight className="nav-arrow" size={15} />
          </button>
          <button
            className={view === "directory" ? "active" : ""}
            onClick={() => switchView("directory")}
            aria-current={view === "directory" ? "page" : undefined}
          >
            <Users size={19} />
            Client directory
          </button>
          <button
            className={view === "activity" ? "active" : ""}
            onClick={() => switchView("activity")}
            aria-current={view === "activity" ? "page" : undefined}
          >
            <History size={19} />
            Activity
          </button>
        </nav>
      </aside>
      <div className="workspace">
        <header className="topbar">
          <div className="breadcrumb">
            Workspace <ChevronRight size={14} />
            <span>
              {view === "overview"
                ? "Overview"
                : view === "directory"
                  ? "Client directory"
                  : "Activity"}
            </span>
          </div>
          <div className="topbar-right">
            <span className={`status-pill ${data.open ? "" : "closed"}`}>
              <span className="status-dot" />
              {loading
                ? "Connecting"
                : connectionError
                  ? "Unavailable"
                  : data.open
                    ? "Branch open"
                    : "Branch closed"}
            </span>
            <span className="branch-code">BRANCH 001</span>
            <button
              className="operator-avatar"
              aria-label="Workspace help"
              onClick={() => setDialog("help")}
            >
              <CircleHelp size={16} />
            </button>
          </div>
        </header>
        <main id="main">
          <section className="page-intro">
            <div>
              <p className="eyebrow">BANKER / OPERATIONS</p>
              <h1>{title}</h1>
              <p>
                {view === "overview"
                  ? "Your queue, your clients, your next move."
                  : view === "directory"
                    ? "Find a client, check a balance, and explore their history."
                    : "A clear history of the work behind each balance."}
              </p>
            </div>
            <button
              className="button primary"
              disabled={
                workLocked || data.accounts.every((a) => a.status === "CLOSED")
              }
              onClick={showCheckIn}
            >
              <Plus size={18} />
              Client check-in
            </button>
          </section>
          <div className="announcements" aria-live="polite">
            {command.notice && (
              <div
                role={command.notice.kind === "error" ? "alert" : "status"}
                className={`notice ${command.notice.kind}`}
              >
                {command.notice.kind === "success" && <Check size={18} />}
                <span>{command.notice.message}</span>
              </div>
            )}
            {connectionError && (
              <div role="alert" className="notice error">
                <span>{connectionError}</span>
                <button
                  className="text-button"
                  disabled={refreshing}
                  onClick={() => void refresh()}
                >
                  Reconnect
                </button>
              </div>
            )}
          </div>
          {command.pending && (
            <section
              className="pending-banner"
              aria-label="Saved pending action"
            >
              <div>
                <strong>
                  {command.busy
                    ? "Confirming your action…"
                    : "One action needs confirmation"}
                </strong>
                <p>{command.pending.label}</p>
                <small>
                  Other actions are paused. This saved retry prevents duplicate
                  submissions, including after a page reload.
                </small>
              </div>
              <button
                className="button primary"
                disabled={command.busy}
                onClick={async () => {
                  const success = await commands.retry();
                  if (success) {
                    setDialog(null);
                    setAmount("");
                  }
                  await refresh();
                }}
              >
                <RefreshCw size={16} />
                {command.busy ? "Confirming…" : "Retry saved action"}
              </button>
            </section>
          )}
          {loading && (
            <div className="loading-panel" role="status">
              <RefreshCw className="spin" size={24} />
              Loading your branch…
            </div>
          )}
          {!loading && (
            <>
              <section className="stats-grid" aria-label="Branch summary">
                <article className="stat-card">
                  <span className="stat-icon">
                    <Users size={21} />
                  </span>
                  <div>
                    <p>Waiting in line</p>
                    <strong>
                      {waiting.length.toString().padStart(2, "0")}
                    </strong>
                    <small>
                      {waiting.length
                        ? "Ready for the next teller"
                        : "The queue is clear"}
                    </small>
                  </div>
                  <span className="stat-tag">FIFO</span>
                </article>
                <article className="stat-card">
                  <span className="stat-icon amber">
                    <Wallet size={21} />
                  </span>
                  <div>
                    <p>In service</p>
                    <strong>
                      {serving.length.toString().padStart(2, "0")}
                    </strong>
                    <small>
                      Active teller{" "}
                      {serving.length === 1 ? "session" : "sessions"}
                    </small>
                  </div>
                  <span className="stat-decoration" aria-hidden="true">
                    ↗
                  </span>
                </article>
                <article className="stat-card">
                  <span className="stat-icon violet">
                    <ShieldCheck size={21} />
                  </span>
                  <div>
                    <p>Open accounts</p>
                    <strong>
                      {data.accounts
                        .filter((a) => a.status === "OPEN")
                        .length.toString()
                        .padStart(2, "0")}
                    </strong>
                    <small>Fictional demo clients</small>
                  </div>
                  <span className="stat-decoration" aria-hidden="true">
                    ↗
                  </span>
                </article>
              </section>
              {view === "overview" && (
                <section className="desk-grid">
                  <article className="panel queue-panel">
                    <header className="panel-header">
                      <div>
                        <h2>
                          Service queue{" "}
                          <span className="count-chip">
                            {data.queue.length}
                          </span>
                        </h2>
                        <p>First in. First served.</p>
                      </div>
                      <span className="live-label">
                        <span className="status-dot" />
                        {connectionError ? "Offline" : "Live"}
                      </span>
                    </header>
                    <div className="queue-label">
                      <span>CLIENT</span>
                      <span>STATUS</span>
                    </div>
                    {data.queue.length === 0 ? (
                      <div className="queue-empty">
                        <span className="empty-art">
                          <Users size={32} />
                          <span>
                            <Check size={12} />
                          </span>
                        </span>
                        <h3>Queue clear.</h3>
                        <p>Check in a client to get the day moving.</p>
                        <button
                          className="text-button"
                          disabled={workLocked}
                          onClick={showCheckIn}
                        >
                          Check in a client <ArrowRight size={15} />
                        </button>
                      </div>
                    ) : (
                      <ul className="queue-list">
                        {data.queue.map((ticket) => {
                          const owner = data.accounts.find(
                            (a) => a.id === ticket.accountId,
                          )!;
                          return (
                            <li key={ticket.id}>
                              <button
                                className={`queue-person ${active?.id === ticket.id ? "selected" : ""}`}
                                disabled={ticket.status === "WAITING"}
                                onClick={() => {
                                  setActiveId(ticket.id);
                                  setAmount("");
                                  setFormError("");
                                }}
                                aria-label={`${fullName(owner)}, ticket ${ticket.id}, ${ticket.status === "SERVING" ? "in service" : "waiting"}`}
                                aria-pressed={
                                  ticket.status === "SERVING"
                                    ? active?.id === ticket.id
                                    : undefined
                                }
                              >
                                <Avatar account={owner} small />
                                <span className="person-info">
                                  <strong>{fullName(owner)}</strong>
                                  <small>
                                    Ticket #{ticket.id} ·{" "}
                                    {ticket.status === "SERVING"
                                      ? ticket.teller
                                      : "Waiting for a teller"}
                                  </small>
                                </span>
                                <span
                                  className={`ticket-status ${ticket.status === "SERVING" ? "serving" : ""}`}
                                >
                                  {ticket.status === "SERVING"
                                    ? "In service"
                                    : "Waiting"}
                                </span>
                              </button>
                            </li>
                          );
                        })}
                      </ul>
                    )}
                    <div className="claim-controls">
                      <label htmlFor="teller">Teller name</label>
                      <div>
                        <input
                          id="teller"
                          value={teller}
                          maxLength={40}
                          onChange={(e) => setTeller(e.target.value)}
                          disabled={locked}
                        />
                        <button
                          className="button primary"
                          disabled={
                            workLocked ||
                            !waiting.length ||
                            !teller.trim() ||
                            serving.some(
                              (t) => t.teller === teller.trim().toLowerCase(),
                            )
                          }
                          onClick={() =>
                            void run(
                              "/queue/claim-next",
                              { teller: teller.trim() },
                              `Next client assigned to ${teller.trim()}`,
                            )
                          }
                        >
                          Serve next
                          <ArrowRight size={16} />
                        </button>
                      </div>
                      <small>
                        <Clock3 size={13} />
                        Clients are served in check-in order.
                      </small>
                    </div>
                  </article>
                  <article className="panel teller-panel">
                    <header className="panel-header">
                      <div>
                        <h2>Teller session</h2>
                        <p>
                          {active
                            ? `Ticket #${active.id} · ${active.teller}`
                            : "Your next interaction starts here."}
                        </p>
                      </div>
                      <span className="outline-chip">
                        {active ? "IN PROGRESS" : "READY TO SERVE"}
                      </span>
                    </header>
                    {active && account ? (
                      <>
                        <div className="active-client">
                          <Avatar account={account} />
                          <div>
                            <h3>{fullName(account)}</h3>
                            <p>{account.id}</p>
                          </div>
                          <span className="verified">
                            <ShieldCheck size={14} />
                            Verified
                          </span>
                        </div>
                        <div className="balance-card">
                          <p>Available balance</p>
                          <strong data-testid="active-balance">
                            {currency(account.balance)}
                          </strong>
                          <span>
                            USD <span>•</span> Demo account
                          </span>
                        </div>
                        <form className="money-form" onSubmit={submitMoney}>
                          <div
                            className="segmented"
                            role="group"
                            aria-label="Transaction type"
                          >
                            <button
                              type="button"
                              className={
                                operation === "deposits" ? "selected" : ""
                              }
                              aria-pressed={operation === "deposits"}
                              onClick={() => {
                                setOperation("deposits");
                                setFormError("");
                              }}
                              disabled={workLocked}
                            >
                              <ArrowDownLeft size={17} />
                              Deposit
                            </button>
                            <button
                              type="button"
                              className={
                                operation === "withdrawals" ? "selected" : ""
                              }
                              aria-pressed={operation === "withdrawals"}
                              onClick={() => {
                                setOperation("withdrawals");
                                setFormError("");
                              }}
                              disabled={workLocked}
                            >
                              <ArrowUpRight size={17} />
                              Withdraw
                            </button>
                          </div>
                          <label htmlFor="amount">Amount</label>
                          <div
                            className={`amount-field ${formError ? "invalid" : ""}`}
                          >
                            <span>$</span>
                            <input
                              id="amount"
                              inputMode="decimal"
                              autoComplete="off"
                              placeholder="0.00"
                              maxLength={16}
                              value={amount}
                              onChange={(e) => setAmount(e.target.value)}
                              disabled={workLocked}
                              aria-invalid={Boolean(formError)}
                              aria-describedby={
                                formError ? "amount-error" : undefined
                              }
                            />
                            <span>USD</span>
                          </div>
                          {formError && (
                            <p
                              id="amount-error"
                              className="field-error"
                              role="alert"
                            >
                              {formError}
                            </p>
                          )}
                          <button
                            className="button primary full-width"
                            disabled={workLocked || !amount.trim()}
                          >
                            {command.busy
                              ? "Confirming…"
                              : operation === "deposits"
                                ? "Confirm deposit"
                                : "Confirm withdrawal"}
                            <ArrowRight size={17} />
                          </button>
                        </form>
                        <div className="session-actions">
                          <button
                            className="text-button"
                            disabled={workLocked}
                            onClick={() =>
                              void run(
                                `/tickets/${active.id}/complete`,
                                undefined,
                                `Session for ${fullName(account)} completed`,
                              )
                            }
                          >
                            <Check size={16} />
                            Complete session
                          </button>
                          <button
                            className="text-button muted"
                            disabled={workLocked || account.balance !== 0}
                            onClick={() => {
                              setClosureTarget({
                                ticketId: active.id,
                                accountId: account.id,
                                name: fullName(account),
                              });
                              setDialog("close-account");
                            }}
                          >
                            Close account
                          </button>
                        </div>
                        <p className="session-hint">
                          Accounts can be closed once their balance reaches
                          $0.00.
                        </p>
                      </>
                    ) : (
                      <div className="session-empty">
                        <div className="desk-illustration" aria-hidden="true">
                          <span />
                          <Wallet size={39} />
                          <i />
                          <b />
                        </div>
                        <p className="eyebrow">TELLER STATION</p>
                        <h3>Ready for the next client.</h3>
                        <p>
                          Choose a teller name and serve the next client.
                          <br />
                          Their account and transaction tools will appear here.
                        </p>
                        <span className="secure-note">
                          <ShieldCheck size={15} />
                          Every confirmed transaction is saved.
                        </span>
                      </div>
                    )}
                  </article>
                </section>
              )}
              {view !== "activity" && (
                <section className="panel directory-panel">
                  <header className="panel-header">
                    <div>
                      <h2>
                        Client directory{" "}
                        <span className="count-chip">
                          {data.accounts.length}
                        </span>
                      </h2>
                      <p>Account balances and client records.</p>
                    </div>
                    <label className="search-field">
                      <Search size={16} />
                      <input
                        aria-label="Search clients"
                        placeholder="Search name or account…"
                        value={query}
                        onChange={(e) => setQuery(e.target.value)}
                      />
                    </label>
                  </header>
                  <div className="table-scroll">
                    <table>
                      <thead>
                        <tr>
                          <th>Client</th>
                          <th>Account number</th>
                          <th className="numeric">Balance</th>
                          <th>Status</th>
                          <th>
                            <span className="sr-only">Actions</span>
                          </th>
                        </tr>
                      </thead>
                      <tbody>
                        {filtered.map((a) => (
                          <tr key={a.id}>
                            <td>
                              <div className="table-person">
                                <Avatar account={a} small />
                                <strong>{fullName(a)}</strong>
                              </div>
                            </td>
                            <td className="account-number">{a.id}</td>
                            <td className="numeric">{currency(a.balance)}</td>
                            <td>
                              <span
                                className={`account-status ${a.status === "CLOSED" ? "closed" : ""}`}
                              >
                                <span className="status-dot" />
                                {a.status === "OPEN" ? "Open" : "Closed"}
                              </span>
                            </td>
                            <td className="numeric">
                              <button
                                className="text-button"
                                onClick={() => setProfileId(a.id)}
                                aria-label={`View account for ${fullName(a)}`}
                              >
                                View account
                                <ChevronRight size={15} />
                              </button>
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                    {filtered.length === 0 && (
                      <div className="table-empty">
                        <Search size={25} />
                        <h3>No clients found</h3>
                        <p>Try a different name or account number.</p>
                      </div>
                    )}
                  </div>
                </section>
              )}
              {view === "activity" && (
                <section className="panel activity-panel">
                  <header className="panel-header">
                    <div>
                      <h2>
                        Transaction history{" "}
                        <span className="count-chip">
                          {data.entries.length}
                        </span>
                      </h2>
                      <p>Most recent first. All demo accounts.</p>
                    </div>
                    <label className="search-field">
                      <Search size={16} />
                      <input
                        aria-label="Search activity"
                        placeholder="Search client or transaction…"
                        value={query}
                        onChange={(e) => setQuery(e.target.value)}
                      />
                    </label>
                  </header>
                  {historyTable()}
                </section>
              )}
            </>
          )}
          <footer className="workspace-footer">
            <span>
              <ShieldCheck size={14} />
              Fictional accounts. Practice with confidence.
            </span>
            <div>
              <button
                className="refresh-button"
                disabled={refreshing}
                onClick={() => void refresh()}
              >
                <RefreshCw className={refreshing ? "spin" : ""} size={13} />
                {refreshing ? "Refreshing" : "Refresh"}
              </button>
              <span>
                {updated
                  ? `Updated ${updated.toLocaleTimeString("en-US", { hour: "numeric", minute: "2-digit" })}`
                  : "Not yet connected"}
              </span>
              <button
                className="text-button"
                disabled={locked || (data.open && data.queue.length > 0)}
                onClick={() => {
                  setBranchTarget(!data.open);
                  setDialog("branch");
                }}
              >
                {data.open ? "Close branch" : "Open branch"}
              </button>
            </div>
          </footer>
        </main>
      </div>
      {dialog === "check-in" && (
        <Modal title="Client check-in" onClose={() => setDialog(null)}>
          <p className="dialog-description">
            Welcome a fictional client to the queue. Verify their name to get
            started.
          </p>
          <form
            className="dialog-form"
            onSubmit={(e) => {
              e.preventDefault();
              if (!workLocked)
                void run(
                  "/queue/check-in",
                  {
                    accountId: checkAccount,
                    firstName: firstName.trim(),
                    lastName: lastName.trim(),
                  },
                  `Check-in for ${firstName.trim()} ${lastName.trim()}`,
                );
            }}
          >
            <label htmlFor="check-account">Account</label>
            <select
              id="check-account"
              value={checkAccount}
              onChange={(e) => setCheckAccount(e.target.value)}
              disabled={workLocked}
            >
              {data.accounts
                .filter((a) => a.status === "OPEN")
                .map((a) => (
                  <option key={a.id} value={a.id}>
                    {a.id} · {fullName(a)}
                  </option>
                ))}
            </select>
            <div className="name-fields">
              <div>
                <label htmlFor="first-name">First name</label>
                <input
                  id="first-name"
                  value={firstName}
                  onChange={(e) => setFirstName(e.target.value)}
                  maxLength={60}
                  required
                  disabled={workLocked}
                  autoComplete="off"
                />
              </div>
              <div>
                <label htmlFor="last-name">Last name</label>
                <input
                  id="last-name"
                  value={lastName}
                  onChange={(e) => setLastName(e.target.value)}
                  maxLength={60}
                  required
                  disabled={workLocked}
                  autoComplete="off"
                />
              </div>
            </div>
            {command.notice?.kind === "error" && (
              <p role="alert" className="field-error">
                {command.notice.message}
              </p>
            )}
            <div className="dialog-actions">
              <button
                type="button"
                className="button secondary"
                onClick={() => setDialog(null)}
              >
                Cancel
              </button>
              <button
                className="button primary"
                disabled={workLocked || !firstName.trim() || !lastName.trim()}
              >
                <Plus size={16} />
                Add to queue
              </button>
            </div>
          </form>
        </Modal>
      )}
      {dialog === "close-account" && closureTarget && closureAccount && (
        <Modal title="Close this demo account?" onClose={() => setDialog(null)}>
          <p className="dialog-description">
            {closureTarget.name} · {closureTarget.accountId}
          </p>
          <p>
            The balance is {currency(closureAccount.balance)}. Closing the
            account ends this session. Its transaction history will remain
            available.
          </p>
          <div className="dialog-actions">
            <button
              className="button secondary"
              onClick={() => setDialog(null)}
            >
              Keep account
            </button>
            <button
              className="button danger"
              disabled={workLocked || closureAccount.balance !== 0}
              onClick={() =>
                void run(
                  `/tickets/${closureTarget.ticketId}/close-account`,
                  undefined,
                  `Account ${closureTarget.accountId} closed`,
                )
              }
            >
              Confirm account closure
            </button>
          </div>
        </Modal>
      )}
      {dialog === "branch" && (
        <Modal
          title={branchTarget ? "Open the branch?" : "Close the branch?"}
          onClose={() => setDialog(null)}
        >
          <p className="dialog-description">
            {branchTarget
              ? "Reopen the workspace to check in clients and start teller sessions."
              : "The queue is clear and every session is complete. You can reopen the branch whenever you are ready."}
          </p>
          <div className="dialog-actions">
            <button
              className="button secondary"
              onClick={() => setDialog(null)}
            >
              Cancel
            </button>
            <button
              className="button primary"
              disabled={locked || (!branchTarget && data.queue.length > 0)}
              onClick={() =>
                void run(
                  `/branch/${branchTarget ? "open" : "close"}`,
                  undefined,
                  `Branch ${branchTarget ? "opened" : "closed"}`,
                )
              }
            >
              {branchTarget
                ? "Confirm branch opening"
                : "Confirm branch closure"}
            </button>
          </div>
        </Modal>
      )}
      {dialog === "help" && (
        <Modal
          title="A simple flow, start to finish."
          onClose={() => setDialog(null)}
        >
          <ol className="help-steps">
            <li>
              <strong>Check in a client.</strong>
              <p>
                Use a fictional account and its matching name from the
                directory.
              </p>
            </li>
            <li>
              <strong>Serve the next in line.</strong>
              <p>
                Enter a teller name. One client can be assigned to each teller
                at a time.
              </p>
            </li>
            <li>
              <strong>Handle their transaction.</strong>
              <p>
                Deposit, withdraw, review the account, then complete the
                session.
              </p>
            </li>
          </ol>
          <div className="help-note">
            <ShieldCheck size={21} />
            <p>
              This is a local simulation with fictional accounts. Teller names
              are labels, and name matching is a practice step. This workspace
              has no sign-in and does not handle real money.
            </p>
          </div>
        </Modal>
      )}
      {profile && (
        <Modal title={fullName(profile)} onClose={() => setProfileId(null)}>
          <div className="profile-summary">
            <div>
              <span className="eyebrow">
                {profile.id} · {profile.status === "OPEN" ? "OPEN" : "CLOSED"}
              </span>
              <strong>{currency(profile.balance)}</strong>
              <small>Current balance</small>
            </div>
            <Avatar account={profile} />
          </div>
          <h3 className="profile-history-title">Account history</h3>
          {historyTable(data.entries.filter((e) => e.accountId === profile.id))}
        </Modal>
      )}
    </div>
  );
}
