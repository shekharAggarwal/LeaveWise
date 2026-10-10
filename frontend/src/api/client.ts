// Typed client for docs/API.md. Every error becomes an ApiError carrying the problem's detail.

import type {
  HolidaysResponse,
  Options,
  ParseResponse,
  PlanRequest,
  PlanResponse,
  Problem,
} from './types';

export class ApiError extends Error {
  readonly status: number;
  readonly problem: Problem | null;

  constructor(status: number, problem: Problem | null) {
    super(problem?.detail ?? 'Something went wrong. Check your connection and try again.');
    this.status = status;
    this.problem = problem;
  }

  /** The message for one field, if the server named it. */
  fieldMessage(field: string): string | undefined {
    return this.problem?.errors.find((e) => e.field === field)?.message;
  }
}

async function request(path: string, init?: RequestInit): Promise<Response> {
  let response: Response;
  try {
    response = await fetch(path, init);
  } catch {
    throw new ApiError(0, null);
  }
  if (!response.ok) {
    const problem = await response.json().then(
      (body) => body as Problem,
      () => null,
    );
    throw new ApiError(response.status, problem);
  }
  return response;
}

function postJson(path: string, body: unknown): Promise<Response> {
  return request(path, {
    method: 'POST',
    headers: {'Content-Type': 'application/json'},
    body: JSON.stringify(body),
  });
}

export async function getOptions(): Promise<Options> {
  return (await request('/api/options')).json() as Promise<Options>;
}

export async function getHolidays(year: number): Promise<HolidaysResponse> {
  return (await request(`/api/holidays?year=${year}`)).json() as Promise<HolidaysResponse>;
}

export async function parseHolidays(year: number, text: string): Promise<ParseResponse> {
  return (await postJson('/api/holidays/parse', {year, text})).json() as Promise<ParseResponse>;
}

export async function planLeaves(body: PlanRequest): Promise<PlanResponse> {
  return (await postJson('/api/plan', body)).json() as Promise<PlanResponse>;
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
  URL.revokeObjectURL(url);
}
