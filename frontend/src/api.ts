const JSON_HEADERS = {
  "Content-Type": "application/json"
};

export async function apiRequest<T>(
  path: string,
  token: string,
  options: RequestInit = {}
): Promise<T> {
  const headers = new Headers(options.headers ?? JSON_HEADERS);
  if (!headers.has("Content-Type")) {
    headers.set("Content-Type", "application/json");
  }
  if (token.trim()) {
    headers.set("X-Dashboard-Token", token.trim());
  }

  const response = await fetch(path, { ...options, headers });
  if (!response.ok) {
    const rawText = await response.text();
    throw new Error(`HTTP ${response.status}: ${rawText}`);
  }

  const contentType = response.headers.get("Content-Type") ?? "";
  if (contentType.includes("application/json")) {
    return (await response.json()) as T;
  }
  return (await response.text()) as T;
}
