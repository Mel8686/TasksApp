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
  tags: string[]
}

/** Which side of the soft delete the list is showing. */
type View = 'active' | 'archived'

/** The bulk operations the server accepts, narrowed per view. */
type Operation = 'ARCHIVE' | 'RESTORE' | 'STATUS'

type Page = {
  content: Task[]
  page: number
  size: number
  total: number
  totalPages: number
}

type Rejection = { row: number; message: string }

const STATUSES = ['TODO', 'DOING', 'DONE']

/** Must match `TaskSort` on the server: a token, never a field name. */
const SORTS = [
  { value: 'newest', label: 'Newest first' },
  { value: 'title', label: 'Title A-Z' },
  { value: '-title', label: 'Title Z-A' },
  { value: 'dueDate', label: 'Due date, soonest' },
  { value: '-dueDate', label: 'Due date, latest' },
]

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

/** "work, Home" -> ["work", "home"]; the server owns de-duplication and limits. */
function splitTags(value: string): string[] {
  return value
    .split(',')
    .map((name) => name.trim().toLowerCase())
    .filter((name) => name !== '')
}

function filterParams(search: string, status: string, tag: string, view: View, sort: string) {
  return new URLSearchParams({
    q: search,
    status,
    tag,
    archived: String(view === 'archived'),
    sort,
  })
}

function pageUrl(search: string, status: string, tag: string, page: number, view: View, sort: string) {
  const params = filterParams(search, status, tag, view, sort)
  params.set('page', String(page))
  return `/api/tasks?${params.toString()}`
}

/** Replace one row, keeping its position: the list is server-ordered. */
function withRow(page: Page, task: Task): Page {
  return { ...page, content: page.content.map((row) => (row.id === task.id ? task : row)) }
}

export default function App() {
  const [data, setData] = useState<Page>(EMPTY_PAGE)
  const [error, setError] = useState<string | null>(null)
  const [query, setQuery] = useState('')
  const [search, setSearch] = useState('')
  const [status, setStatus] = useState('')
  const [tagDraft, setTagDraft] = useState('')
  const [tag, setTag] = useState('')
  const [view, setView] = useState<View>('active')
  const [sort, setSort] = useState('newest')
  const [page, setPage] = useState(0)
  const [reloadKey, setReloadKey] = useState(0)
  const [title, setTitle] = useState('')
  const [dueDate, setDueDate] = useState('')
  const [newTags, setNewTags] = useState('')
  const [editing, setEditing] = useState<Task | null>(null)
  const [editTags, setEditTags] = useState('')
  const [renaming, setRenaming] = useState<{ id: number; title: string; version: number } | null>(null)
  const [savingId, setSavingId] = useState<number | null>(null)
  const [selected, setSelected] = useState<Set<number>>(new Set())
  const [operation, setOperation] = useState<Operation>('ARCHIVE')
  const [bulkStatus, setBulkStatus] = useState('DOING')
  const [busy, setBusy] = useState(false)
  const [notice, setNotice] = useState<string | null>(null)
  const [rejected, setRejected] = useState<Rejection[]>([])

  // Debounce typing into the server-side search, so each keystroke is not a query.
  useEffect(() => {
    const timer = setTimeout(() => setSearch(query.trim()), 300)
    return () => clearTimeout(timer)
  }, [query])

  useEffect(() => {
    const timer = setTimeout(() => setTag(tagDraft.trim().toLowerCase()), 300)
    return () => clearTimeout(timer)
  }, [tagDraft])

  // One request per change; a superseded request is aborted, so a slow response
  // cannot overwrite a newer one.
  useEffect(() => {
    const controller = new AbortController()
    call<Page>(pageUrl(search, status, tag, page, view, sort), { signal: controller.signal })
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
  }, [search, status, tag, page, view, sort, reloadKey])

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
    return `/api/tasks/export?${filterParams(search, status, tag, view, sort).toString()}`
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

  function changeTag(value: string) {
    setTagDraft(value)
    setPage(0)
  }

  function changeSort(value: string) {
    setSort(value)
    setPage(0)
    setSelected(new Set())
  }

  function changeView(next: View) {
    setView(next)
    setPage(0)
    setEditing(null)
    setNotice(null)
    setSelected(new Set())
    setOperation(next === 'active' ? 'ARCHIVE' : 'RESTORE')
  }

  function add(event: FormEvent) {
    event.preventDefault()
    void mutate(async () => {
      await call<Task>('/api/tasks', {
        method: 'POST',
        body: JSON.stringify({ title, status: 'TODO', dueDate: dueDate || null, tags: splitTags(newTags) }),
      })
      setTitle('')
      setDueDate('')
      setNewTags('')
      setView('active')
      setPage(0)
      reload()
    })
  }

  function startEdit(task: Task) {
    setEditing(task)
    setEditTags(task.tags.join(', '))
    setRenaming(null)
  }

  function beginRename(task: Task) {
    setEditing(null)
    setRenaming({ id: task.id, title: task.title, version: task.version })
  }

  /**
   * Optimistic rename: the row shows the new title before the request lands, so
   * typing never waits on the server. A failure puts the old title back on
   * whatever the row currently holds — not on a snapshot taken before the
   * request, which could be older than a reload that landed meanwhile. A 412
   * means the write was based on a version that has moved on, so the list is
   * refreshed rather than retried silently.
   */
  async function commitRename() {
    if (!renaming) return
    const original = data.content.find((row) => row.id === renaming.id)
    const next = renaming.title.trim()
    setRenaming(null)
    if (!original || next === '' || next === original.title) return

    setSavingId(original.id)
    setError(null)
    setData((current) => withRow(current, { ...original, title: next }))
    try {
      const saved = await call<Task>(`/api/tasks/${original.id}`, {
        method: 'PATCH',
        headers: { 'If-Match': `"${renaming.version}"` },
        body: JSON.stringify({ title: next }),
      })
      setData((current) => withRow(current, saved))
    } catch (cause) {
      setData((current) => {
        const row = current.content.find((candidate) => candidate.id === original.id)
        return row ? withRow(current, { ...row, title: original.title }) : current
      })
      if (cause instanceof ApiError && cause.status === 412) {
        const current = (cause.problem as { current?: Task } | undefined)?.current
        setNotice(
          current
            ? `Someone else saved this task first. It is now "${current.title}"; the list has been refreshed.`
            : 'Someone else saved this task first. The list has been refreshed.',
        )
        reload()
      } else {
        setError(cause instanceof Error ? cause.message : 'Rename failed')
      }
    } finally {
      setSavingId(null)
    }
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
            tags: splitTags(editTags),
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
      setSelected((current) => {
        if (!current.has(task.id)) return current
        const next = new Set(current)
        next.delete(task.id)
        return next
      })
      setNotice(`Archived "${task.title}". Switch to Archived to restore it.`)
      stepBackIfLast()
    })
  }

  function restore(task: Task) {
    void mutate(async () => {
      await call<Task>(`/api/tasks/${task.id}/restore`, { method: 'POST' })
      setSelected((current) => {
        if (!current.has(task.id)) return current
        const next = new Set(current)
        next.delete(task.id)
        return next
      })
      setNotice(`Restored "${task.title}". Switch to Active to see it.`)
      stepBackIfLast()
    })
  }

  function toggleOne(id: number) {
    setSelected((current) => {
      const next = new Set(current)
      if (next.has(id)) next.delete(id)
      else next.add(id)
      return next
    })
  }

  function togglePage(checked: boolean) {
    setSelected((current) => {
      const next = new Set(current)
      for (const task of data.content) {
        if (checked) next.add(task.id)
        else next.delete(task.id)
      }
      return next
    })
  }

  async function applyBulk() {
    const ids = Array.from(selected)
    if (ids.length === 0) return
    const chosen = operation
    const status = chosen === 'STATUS' ? bulkStatus : null
    await mutate(async () => {
      const result = await call<{ updated: number }>('/api/tasks/bulk', {
        method: 'POST',
        body: JSON.stringify({ ids, operation: chosen, status }),
      })
      const noun = result.updated === 1 ? 'task' : 'tasks'
      setNotice(
        chosen === 'ARCHIVE'
          ? `Archived ${result.updated} ${noun}.`
          : chosen === 'RESTORE'
            ? `Restored ${result.updated} ${noun}.`
            : `Set ${result.updated} ${noun} to ${status}.`,
      )
      // Everything selected was on this page and has now left the view: go back
      // instead of showing an empty page beyond the end of the list.
      const pageEmptied = data.content.every((task) => selected.has(task.id))
      setSelected(new Set())
      if (pageEmptied && page > 0) setPage((current) => current - 1)
      else reload()
    })
  }

  const totalPages = Math.max(data.totalPages, 1)
  const pageSelected = data.content.length > 0 && data.content.every((task) => selected.has(task.id))
  const operations: Operation[] = view === 'active' ? ['ARCHIVE', 'STATUS'] : ['RESTORE']
  const filtered = search !== '' || status !== '' || tag !== ''

  return (
    <div className="app">
      <header>
        <h1>Tasks</h1>
        <p>
          No auth by design. Search, tag and status filters are server-side. Deleting archives
          a task; nothing is lost. Tick rows to act on them in bulk.
        </p>
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
        <input
          value={newTags}
          onChange={(event) => setNewTags(event.target.value)}
          placeholder="Tags, comma-separated (max 5)"
          aria-label="Tags for the new task"
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
        <input
          value={tagDraft}
          onChange={(event) => changeTag(event.target.value)}
          placeholder="Filter by tag"
          aria-label="Filter by tag"
        />
        <select
          value={status}
          onChange={(event) => changeStatus(event.target.value)}
          aria-label="Filter by status"
        >
          <option value="">All statuses</option>
          {STATUSES.map((value) => <option key={value} value={value}>{value}</option>)}
        </select>
        <select
          value={sort}
          onChange={(event) => changeSort(event.target.value)}
          aria-label="Sort tasks"
        >
          {SORTS.map((option) => (
            <option key={option.value} value={option.value}>{option.label}</option>
          ))}
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

      {selected.size > 0 ? (
        <div className="bulk" role="group" aria-label="Bulk actions">
          <strong>{selected.size} selected</strong>
          <select
            value={operation}
            onChange={(event) => setOperation(event.target.value as Operation)}
            aria-label="Bulk operation"
          >
            {operations.map((value) => (
              <option key={value} value={value}>
                {value === 'ARCHIVE' ? 'Archive' : value === 'RESTORE' ? 'Restore' : 'Set status'}
              </option>
            ))}
          </select>
          {operation === 'STATUS' ? (
            <select
              value={bulkStatus}
              onChange={(event) => setBulkStatus(event.target.value)}
              aria-label="Status to set"
            >
              {STATUSES.map((value) => <option key={value} value={value}>{value}</option>)}
            </select>
          ) : null}
          <button type="button" onClick={() => void applyBulk()} disabled={busy}>Apply</button>
          <button type="button" onClick={() => setSelected(new Set())}>Clear</button>
        </div>
      ) : null}

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
            <th className="pick">
              <input
                type="checkbox"
                checked={pageSelected}
                onChange={(event) => togglePage(event.target.checked)}
                aria-label="Select every task on this page"
              />
            </th>
            <th>Title</th>
            <th>Tags</th>
            <th>Status</th>
            <th>Due</th>
            <th />
          </tr>
        </thead>
        <tbody>
          {data.content.map((task) => (
            editing?.id === task.id ? (
              <tr key={task.id} className="editing">
                <td className="pick" />
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
                  <input
                    value={editTags}
                    onChange={(event) => setEditTags(event.target.value)}
                    placeholder="work, home"
                    aria-label="Tags"
                  />
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
              <tr key={task.id} className={savingId === task.id ? 'saving' : undefined}>
                <td className="pick">
                  <input
                    type="checkbox"
                    checked={selected.has(task.id)}
                    onChange={() => toggleOne(task.id)}
                    aria-label={`Select ${task.title}`}
                  />
                </td>
                <td>
                  {renaming?.id === task.id ? (
                    <form
                      className="rename"
                      onSubmit={(event) => {
                        event.preventDefault()
                        void commitRename()
                      }}
                    >
                      <input
                        value={renaming.title}
                        autoFocus
                        maxLength={200}
                        required
                        aria-label="New title"
                        onChange={(event) => setRenaming({ ...renaming, title: event.target.value })}
                        onKeyDown={(event) => {
                          if (event.key === 'Escape') setRenaming(null)
                        }}
                      />
                    </form>
                  ) : (
                    <button
                      type="button"
                      className="title-link"
                      title="Rename"
                      disabled={savingId === task.id}
                      onClick={() => beginRename(task)}
                    >
                      {task.title}
                    </button>
                  )}
                </td>
                <td className="tags">
                  {task.tags.map((name) => (
                    <button
                      key={name}
                      type="button"
                      className="chip"
                      onClick={() => changeTag(name)}
                      title={`Show only tasks tagged ${name}`}
                    >
                      {name}
                    </button>
                  ))}
                </td>
                <td><span className={`status ${task.status.toLowerCase()}`}>{task.status}</span></td>
                <td>{task.dueDate ?? '—'}</td>
                <td className="actions">
                  {view === 'active' ? (
                    <>
                      <button type="button" onClick={() => startEdit(task)}>Edit</button>
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
              <td colSpan={6} className="empty">
                {filtered
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
