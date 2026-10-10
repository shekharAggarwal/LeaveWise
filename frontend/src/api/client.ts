// Typed client for docs/API.md. Every error becomes an ApiError carrying the problem's detail.

import type {
  HolidaysResponse,
  Options,
  ParseResponse,
  PlanRequest,
  PlanResponse,
  Problem,
} from './types';

export const CONNECTION_MESSAGE = "Couldn't reach LeaveWise. Check your connection and try again.";

export class ApiError extends Error {
  readonly status: number;
  readonly problem: Problem | null;

  constructor(status: number, problem: Problem | null) {
    super(problem?.detail ?? CONNECTION_MESSAGE);
    this.status = status;
    this.problem = problem;
  }

  /** The message for one field, if the server named it. */
  fieldMessage(field: string): string | undefined {
    return this.problem?.errors.find((e) => e.field === field)?.message;
  }
}

/** A user-facing message for any failure: the server's words when it gave some, else what to do. */
export function messageOf(error: unknown): string {
  return error instanceof ApiError ? error.message : CONNECTION_MESSAGE;
}

/** Accepts any error body; keeps only well-formed parts so a proxy's JSON can't break the page. */
function toProblem(status: number, body: unknown): Problem | null {
  if (typeof body !== 'object' || body === null) return null;
  const b = body as Record<string, unknown>;
  if (typeof b.detail !== 'string' || b.detail.trim() === '') return null;
  const errors = Array.isArray(b.errors)
    ? b.errors.filter(
        (e): e is {field: string; message: string} =>
          typeof e === 'object' &&
          e !== null &&
          typeof (e as Record<string, unknown>).field === 'string' &&
          typeof (e as Record<string, unknown>).message === 'string',
      )
    : [];
  return {
    type: typeof b.type === 'string' ? b.type : 'about:blank',
    title: typeof b.title === 'string' ? b.title : '',
    status,
    detail: b.detail,
    errors,
  };
}

async function request(path: string, init?: RequestInit): Promise<Response> {
  let response: Response;
  try {
    response = await fetch(path, init);
  } catch {
    throw new ApiError(0, null);
  }
  if (!response.ok) {
    const body = await response.json().catch(() => null);
    throw new ApiError(response.status, toProblem(response.status, body));
  }
  return response;
}

async function json<T>(response: Response): Promise<T> {
  try {
    return (await response.json()) as T;
  } catch {
    throw new ApiError(response.status, null);
  }
}

function postJson(path: string, body: unknown): Promise<Response> {
  return request(path, {
    method: 'POST',
    headers: {'Content-Type': 'application/json'},
    body: JSON.stringify(body),
  });
}

export async function getOptions(): Promise<Options> {
  return json<Options>(await request('/api/options'));
}

export async function getHolidays(year: number): Promise<HolidaysResponse> {
  return json<HolidaysResponse>(await request(`/api/holidays?year=${year}`));
}

export async function parseHolidays(year: number, text: string): Promise<ParseResponse> {
  return json<ParseResponse>(await postJson('/api/holidays/parse', {year, text}));
}

export async function planLeaves(body: PlanRequest): Promise<PlanResponse> {
  return json<PlanResponse>(await postJson('/api/plan', body));
}

/** Fetches the .ics for the same inputs and hands it to the browser as a download. */
export async function downloadCalendar(body: PlanRequest): Promise<void> {
  const blob = await (await postJson('/api/plan/ics', body)).blob();
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = `leavewise-${body.year}.ics`;
  document.body.append(link);
  link.click();
  link.remove();
  // Revoking synchronously can cancel the download in Safari and some Firefox versions.
  window.setTimeout(() => URL.revokeObjectURL(url), 60_000);
}
