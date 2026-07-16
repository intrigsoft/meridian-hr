/**
 * Minimal HTTP session with a cookie jar. The jar holds the legacy app's session
 * cookie (Meridian's `meridian_device`) and is forwarded on every request — it is
 * NEVER serialized into a tool result, so the LLM never sees it (credential-blind).
 *
 * Redirects are handled manually: the login POST answers 302, and we only need the
 * Set-Cookie it produced, not the page it points at.
 */
export interface HttpResult {
  status: number;
  body: string;
  location: string | null;
}

export class Session {
  /** Meridian's session cookie (see DeviceFilter.COOKIE). */
  static readonly DEVICE_COOKIE = "meridian_device";

  private cookies = new Map<string, string>();

  constructor(readonly baseUrl: string) {}

  /** Seed a cookie directly — used to forward a real, already-authenticated legacy
   *  session (BYOA pass-through) instead of logging in. Credential-blind: the value
   *  lives only in the jar and is never serialized into a tool result. */
  seedCookie(name: string, value: string): void {
    this.cookies.set(name, value);
  }

  private cookieHeader(): string {
    return [...this.cookies].map(([k, v]) => `${k}=${v}`).join("; ");
  }

  private absorb(res: Response): void {
    // Node 20+ (undici) exposes getSetCookie(); fall back to the single header.
    const raw: string[] =
      (res.headers as unknown as { getSetCookie?: () => string[] }).getSetCookie?.() ??
      (res.headers.get("set-cookie") ? [res.headers.get("set-cookie") as string] : []);
    for (const c of raw) {
      const pair = c.split(";")[0];
      const eq = pair.indexOf("=");
      if (eq > 0) this.cookies.set(pair.slice(0, eq).trim(), pair.slice(eq + 1).trim());
    }
  }

  async request(method: string, path: string, form?: Record<string, string>): Promise<HttpResult> {
    const headers: Record<string, string> = {};
    const cookie = this.cookieHeader();
    if (cookie) headers["cookie"] = cookie;
    let body: string | undefined;
    if (form) {
      headers["content-type"] = "application/x-www-form-urlencoded";
      body = new URLSearchParams(form).toString();
    }
    const res = await fetch(new URL(path, this.baseUrl), { method, headers, body, redirect: "manual" });
    this.absorb(res);
    return { status: res.status, body: await res.text(), location: res.headers.get("location") };
  }

  get(path: string): Promise<HttpResult> {
    return this.request("GET", path);
  }

  post(path: string, form: Record<string, string>): Promise<HttpResult> {
    return this.request("POST", path, form);
  }
}
