import { useEffect, useState } from 'react'
import type { ChangeEvent, FormEvent, MouseEvent } from 'react'

type Task = {
  id: number
  title: string
  status: string
  dueDate: string | null
  createdAt: string
  archivedAt: string | null
  version: number
}

/** Which side of the soft delete the list is showing. */
type View = 'active' | 'archived'

type Page = {
  content: Task[]
  page: number
  size: number
  total: number
  totalPages: number
}

type Rejection = { row: number; message: string }

const STATUSES = ['TODO', 'DOING', 'DONE']

const EMPTY_PAGE: Page = { content: [], page: 0, size: 5, total: 0, totalPages: 0 }

function describeFailure(status: number, statusText: string, problem: unknown) {
  if (problem && typeof problem === 'object') {
    const { detail, errors } = problem as { detail?: unknown; errors?: unknown }
    const parts: string[] = []
    if (typeof detail === 'string') parts.push(detail)
    if (errors && typeof errors === 'object') {
      parts.push(
        Object.entries(errors as Record<string, unknown>)
          .map(([field, message]) => `${field}: ${String(message)}`)
          .join('; '),
      )
    }
    if (parts.length > 0) return parts.join(' — ')
  }
  return `${status} ${statusText}`
}

/** Keeps the HTTP status and the ProblemDetail body, so callers can branch on it. */
class ApiError extends Error {
  readonly status: number
  readonly problem: unknown

  constructor(status: number, statusText: string, problem: unknown) {
    super(describeFailure(status, statusText, problem))
    this.name = 'ApiError'
    this.status = status
    this.problem = problem
  }
}

async function call<T>(url: string, init?: RequestInit): Promise<T> {
  const formData = init?.body instanceof FormData
  const response = await fetch(url, {
    ...init,
    headers: {
      ...(formData ? {} : { 'Content-Type': 'application/json' }),
      ...(init?.headers ?? {}),
    },
  })
  if (!response.ok) {
    let problem: unknown
    try {
      problem = await response.json()
    } catch {
      problem = undefined
    }
    throw new ApiError(response.status, response.statusText, problem)
  }
  return response.status === 204 ? (undefined as T) : ((await response.json()) as T)
}

function pageUrl(search: string, status: string, page: number, view: View) {
  const params = new URLSearchParams({
    q: search,
    status,
    page: String(page),
    archived: String(view === 'archived'),
  })
  return `/api/tasks?${params.toString()}`
}

export default function App() {
  const [data, setData] = useState<Page>(EMPTY_PAGE)
  const [error, setError] = useState<string | null>(null)
  const [query, setQuery] = useState('')
  const [search, setSearch] = useState('')
  const [status, setStatus] = useState('')
  const [view, setView] = useState<View>('active')
  const [page, setPage] = useState(0)
  const [reloadKey, setReloadKey] = useState(0)
  const [title, setTitle] = useState('')
  const [dueDate, setDueDate] = useState('')
  const [editing, setEditing] = useState<Task | null>(null)
  const [busy, setBusy] = useState(false)
  const [notice, setNotice] = useState<string | null>(null)
  const [rejected, setRejected] = useState<Rejection[]>([])

  // Debounce typing into the server-side search, so each keystroke is not a query.
  useEffect(() => {
    const timer = setTimeout(() => setSearch(query.trim()), 300)
    return () => clearTimeout(timer)
  }, [query])

  // One request per change; a superseded request is aborted, so a slow response
  // cannot overwrite a newer one.
  useEffect(() => {
    const controller = new AbortController()
    call<Page>(pageUrl(search, status, page, view), { signal: controller.signal })
      .then((result) => {
        setData(result)
        setError(null)
      })
      .catch((cause: unknown) => {
        if (!controller.signal.aborted) {
          setError(cause instanceof Error ? cause.message : 'Request failed')
        }
      })
    return () => controller.abort()
  }, [search, status, page, view, reloadKey])

  async function mutate(action: () => Promise<void>) {
    setBusy(true)
    setError(null)
    setNotice(null)
    setRejected([])
    try {
      await action()
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Request failed')
    } finally {
      setBusy(false)
    }
  }

  async function importCsv(file: File) {
    setBusy(true)
    setError(null)
    setNotice(null)
    setRejected([])
    try {
      const form = new FormData()
      form.append('file', file)
      const response = await fetch('/api/tasks/import', { method: 'POST', body: form })
      if (!response.ok) {
        const problem = (await response.json().catch(() => null)) as
          | { detail?: string; rejected?: Rejection[] }
          | null
        if (Array.isArray(problem?.rejected)) {
          setRejected(problem.rejected)
        }
        throw new Error(problem?.detail ?? `${response.status} ${response.statusText}`)
      }
      const result = (await response.json()) as { imported: number }
      setNotice(`Imported ${result.imported} ${result.imported === 1 ? 'task' : 'tasks'}.`)
      setView('active')
      setPage(0)
      reload()
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Import failed')
    } finally {
      setBusy(false)
    }
  }

  function pickFile(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0]
    event.target.value = ''
    if (file) void importCsv(file)
  }

  function exportHref() {
    const params = new URLSearchParams({
      q: search,
      status,
      archived: String(view === 'archived'),
    })
    return `/api/tasks/export?${params.toString()}`
  }

  function reload() {
    setReloadKey((key) => key + 1)
  }

  function changeQuery(value: string) {
    setQuery(value)
    setPage(0)
  }

  function changeStatus(value: string) {
    setStatus(value)
    setPage(0)
  }

  function changeView(next: View) {
    setView(next)
    setPage(0)
    setEditing(null)
    setNotice(null)
  }

  function add(event: FormEvent) {
    event.preventDefault()
    void mutate(async () => {
      await call<Task>('/api/tasks', {
        method: 'POST',
        body: JSON.stringify({ title, status: 'TODO', dueDate: dueDate || null }),
      })
      setTitle('')
      setDueDate('')
      setView('active')
      setPage(0)
      reload()
    })
  }

  function saveEdit(event: FormEvent | MouseEvent) {
    event.preventDefault()
    if (!editing) return
    const target = editing
    void mutate(async () => {
      try {
        await call<Task>(`/api/tasks/${target.id}`, {
          method: 'PUT',
          headers: { 'If-Match': `"${target.version}"` },
          body: JSON.stringify({
            title: target.title,
            status: target.status,
            dueDate: target.dueDate,
          }),
        })
        setEditing(null)
        reload()
      } catch (cause) {
        // 412 means someone saved first. Closing the editor is deliberate:
        // keeping it open would let the next Save overwrite them silently.
        if (cause instanceof ApiError && cause.status === 412) {
          const current = (cause.problem as { current?: Task } | undefined)?.current
          setEditing(null)
          setNotice(
            current
              ? `Someone else saved this task first. It is now "${current.title}" (${current.status}); the list has been refreshed.`
              : 'Someone else saved this task first. The list has been refreshed.',
          )
          reload()
          return
        }
        throw cause
      }
    })
  }

  /** The row leaves the current view, so step back a page when it was the last one. */
  function stepBackIfLast() {
    if (data.content.length === 1 && page > 0) {
      setPage((current) => current - 1)
    } else {
      reload()
    }
  }

  function archive(task: Task) {
    void mutate(async () => {
      await call<void>(`/api/tasks/${task.id}`, { method: 'DELETE' })
      setNotice(`Archived "${task.title}". Switch to Archived to restore it.`)
      stepBackIfLast()
    })
  }

  function restore(task: Task) {
    void mutate(async () => {
      await call<Task>(`/api/tasks/${task.id}/restore`, { method: 'POST' })
      setNotice(`Restored "${task.title}". Switch to Active to see it.`)
      stepBackIfLast()
    })
  }

  const totalPages = Math.max(data.totalPages, 1)

  return (
    <div className="app">
      <header>
        <h1>Tasks</h1>
        <p>No auth by design. Search, status filter and paging are server-side. Deleting archives a task; nothing is lost.</p>
      </header>

      <form className="add" onSubmit={add}>
        <input
          value={title}
          onChange={(event) => setTitle(event.target.value)}
          placeholder="What needs doing?"
          required
        />
        <input
          type="date"
          value={dueDate}
          onChange={(event) => setDueDate(event.target.value)}
        />
        <button type="submit" disabled={busy}>Add</button>
      </form>

      <div className="filters">
        <input
          type="search"
          value={query}
          onChange={(event) => changeQuery(event.target.value)}
          placeholder="Search titles"
          aria-label="Search titles"
        />
        <select
          value={status}
          onChange={(event) => changeStatus(event.target.value)}
          aria-label="Filter by status"
        >
          <option value="">All statuses</option>
          {STATUSES.map((value) => <option key={value} value={value}>{value}</option>)}
        </select>
        <div className="segmented" role="group" aria-label="Show active or archived tasks">
          <button
            type="button"
            aria-pressed={view === 'active'}
            onClick={() => changeView('active')}
          >
            Active
          </button>
          <button
            type="button"
            aria-pressed={view === 'archived'}
            onClick={() => changeView('archived')}
          >
            Archived
          </button>
        </div>
      </div>

      <div className="toolbar">
        <a className="button" href={exportHref()}>Export CSV</a>
        <label className="button">
          Import CSV
          <input type="file" accept=".csv,text/csv" onChange={pickFile} hidden />
        </label>
      </div>

      {error ? <p className="error" role="alert">{error}</p> : null}
      {notice ? <p className="notice" role="status">{notice}</p> : null}
      {rejected.length > 0 ? (
        <ul className="rejected">
          {rejected.map((item) => <li key={item.row}>{`Line ${item.row}: ${item.message}`}</li>)}
        </ul>
      ) : null}

      <table>
        <thead>
          <tr>
            <th>Title</th>
            <th>Status</th>
            <th>Due</th>
            <th />
          </tr>
        </thead>
        <tbody>
          {data.content.map((task) => (
            editing?.id === task.id ? (
              <tr key={task.id} className="editing">
                <td>
                  <form id={`edit-${task.id}`} onSubmit={saveEdit}>
                    <input
                      value={editing.title}
                      autoFocus
                      onChange={(event) => setEditing({ ...editing, title: event.target.value })}
                      required
                    />
                  </form>
                </td>
                <td>
                  <select
                    value={editing.status}
                    onChange={(event) => setEditing({ ...editing, status: event.target.value })}
                  >
                    {STATUSES.map((value) => <option key={value} value={value}>{value}</option>)}
                  </select>
                </td>
                <td>
                  <input
                    type="date"
                    value={editing.dueDate ?? ''}
                    onChange={(event) => setEditing({ ...editing, dueDate: event.target.value || null })}
                  />
                </td>
                <td className="actions">
                  <button type="button" onClick={saveEdit} disabled={busy}>Save</button>
                  <button type="button" onClick={() => setEditing(null)}>Cancel</button>
                </td>
              </tr>
            ) : (
              <tr key={task.id}>
                <td>{task.title}</td>
                <td><span className={`status ${task.status.toLowerCase()}`}>{task.status}</span></td>
                <td>{task.dueDate ?? '—'}</td>
                <td className="actions">
                  {view === 'active' ? (
                    <>
                      <button type="button" onClick={() => setEditing(task)}>Edit</button>
                      <button type="button" disabled={busy} onClick={() => archive(task)}>
                        Archive
                      </button>
                    </>
                  ) : (
                    <button type="button" disabled={busy} onClick={() => restore(task)}>
                      Restore
                    </button>
                  )}
                </td>
              </tr>
            )
          ))}
          {data.content.length === 0 ? (
            <tr>
              <td colSpan={4} className="empty">
                {search !== '' || status !== ''
                  ? 'No tasks match those filters.'
                  : view === 'archived'
                    ? 'Nothing archived.'
                    : 'No tasks yet.'}
              </td>
            </tr>
          ) : null}
        </tbody>
      </table>

      <nav className="pager" aria-label="Pagination">
        <button type="button" onClick={() => setPage((current) => Math.max(0, current - 1))} disabled={page === 0 || busy}>
          Prev
        </button>
        <span>Page {page + 1} of {totalPages} · {data.total} {data.total === 1 ? 'task' : 'tasks'}</span>
        <button type="button" onClick={() => setPage((current) => current + 1)} disabled={page + 1 >= data.totalPages || busy}>
          Next
        </button>
      </nav>
    </div>
  )
}
