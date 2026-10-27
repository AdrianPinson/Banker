export interface Account {
  id: string;
  firstName: string;
  lastName: string;
  balance: number;
  status: "OPEN" | "CLOSED";
}
export interface Ticket {
  id: number;
  accountId: string;
  status: "WAITING" | "SERVING" | "COMPLETED";
  teller: string | null;
  checkedInAt: string;
  servedAt: string | null;
  completedAt: string | null;
}
export interface Entry {
  id: number;
  accountId: string;
  ticketId: number;
  kind: "DEPOSIT" | "WITHDRAWAL" | "CLOSE_ACCOUNT";
  amount: number;
  balanceAfter: number;
  createdAt: string;
}
export interface Snapshot {
  accounts: Account[];
  queue: Ticket[];
  entries: Entry[];
  open: boolean;
}
export function fullName(a: Account) {
  return `${a.firstName} ${a.lastName}`;
}
export const currency = (amount: number) =>
  new Intl.NumberFormat("en-US", { style: "currency", currency: "USD" }).format(
    amount,
  );
export const dateTime = (value: string) =>
  new Intl.DateTimeFormat("en-US", {
    month: "short",
    day: "numeric",
    hour: "numeric",
    minute: "2-digit",
  }).format(new Date(value));
export function moneyInput(value: string): string | null {
  // Validate decimal text without floating-point arithmetic or rounding a fractional cent.
  const text = value.trim();
  if (!/^\d{1,13}(\.\d{1,2})?$/.test(text) || !/[1-9]/.test(text)) return null;
  const [whole, fraction = ""] = text.split(".");
  return `${whole.replace(/^0+(?=\d)/, "")}.${fraction.padEnd(2, "0")}`;
}
