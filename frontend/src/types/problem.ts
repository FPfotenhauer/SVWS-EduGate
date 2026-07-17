/** Fehlerformat gemäß RFC 7807, wie es die Control-Plane-API zurückliefert. */
export interface ProblemDetail {
  type: string
  title: string
  status: number
  detail: string | null
  instance: string | null
}

export class ApiError extends Error {
  readonly problem: ProblemDetail | null
  readonly status: number

  constructor(status: number, problem: ProblemDetail | null) {
    super(problem?.detail ?? problem?.title ?? `Anfrage fehlgeschlagen (Status ${status}).`)
    this.name = 'ApiError'
    this.status = status
    this.problem = problem
  }
}
