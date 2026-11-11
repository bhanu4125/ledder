import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import { Link, Navigate, Route, Routes, useLocation, useNavigate } from 'react-router-dom'

const ApiContext = createContext(null)
const navigation = [
  { label: 'Overview', path: '/', icon: '◫' },
  { label: 'Transactions', path: '/transactions', icon: '↔' },
  { label: 'Ledger', path: '/ledger', icon: '▤' },
  { label: 'Authorisation', path: '/authorisation', icon: '✓' },
  { label: 'Audit & security', path: '/audit', icon: '◉', roles: ['ADMIN', 'AUDITOR'] },
  { label: 'Settings', path: '/settings', icon: '⚙' },
]

function useApi() { return useContext(ApiContext) }

function App() {
  const [session, setSession] = useState(null)
  const [initializing, setInitializing] = useState(true)
  const [authError, setAuthError] = useState('')
  const [runtimeEnvironment, setRuntimeEnvironment] = useState('UNKNOWN')
  const location = useLocation()
  const navigate = useNavigate()

  useEffect(() => {
    let active = true
    let timer = null
    function probeHealth() {
      fetch('/api/health')
        .then((response) => response.ok ? response.json() : null)
        .then((health) => {
          if (active && health?.environment) {
            setRuntimeEnvironment(health.environment)
          } else if (active) {
            setRuntimeEnvironment('STARTING_UP')
            timer = setTimeout(probeHealth, 3000)
          }
        })
        .catch(() => {
          if (active) {
            setRuntimeEnvironment('STARTING_UP')
            timer = setTimeout(probeHealth, 3000)
          }
        })
    }
    probeHealth()
    return () => { active = false; if (timer) clearTimeout(timer) }
  }, [])

  const refresh = useCallback(async () => {
    const response = await fetch('/api/auth/refresh', { method: 'POST', credentials: 'include' })
    if (!response.ok) throw new Error('Your session has expired. Sign in again.')
    return response.json()
  }, [])

  useEffect(() => {
    let active = true
    refresh().then((data) => { if (active) setSession(data) })
      .catch(() => { if (active) setSession(null) })
      .finally(() => { if (active) setInitializing(false) })
    return () => { active = false }
  }, [refresh])

  const request = useCallback(async (path, options = {}, retried = false) => {
    const headers = new Headers(options.headers || {})
    if (!headers.has('X-Correlation-ID')) headers.set('X-Correlation-ID', crypto.randomUUID())
    if (session?.accessToken) headers.set('Authorization', `Bearer ${session.accessToken}`)
    if (options.body && !headers.has('Content-Type')) headers.set('Content-Type', 'application/json')
    let response = await fetch(path, { ...options, headers, credentials: 'include' })
    if (response.status === 401 && !retried && path !== '/api/auth/login') {
      try {
        const renewed = await refresh()
        setSession(renewed)
        const retryHeaders = new Headers(options.headers || {})
        retryHeaders.set('X-Correlation-ID', headers.get('X-Correlation-ID'))
        retryHeaders.set('Authorization', `Bearer ${renewed.accessToken}`)
        if (options.body) retryHeaders.set('Content-Type', 'application/json')
        response = await fetch(path, { ...options, headers: retryHeaders, credentials: 'include' })
      } catch {
        setSession(null)
        throw new Error('Your session has expired. Sign in again.')
      }
    }
    if (!response.ok) {
      let payload = {}
      try { payload = await response.json() } catch { /* Response may be empty. */ }
      const correlation = payload.correlationId ? ` (reference ${payload.correlationId})` : ''
      throw new Error(`${payload.message || `Request failed (${response.status})`}${correlation}`)
    }
    if (response.status === 204) return null
    if (response.headers.get('Content-Type')?.startsWith('text/csv')) return response.blob()
    return response.json()
  }, [refresh, session])

  const api = useMemo(() => ({ request, session }), [request, session])

  async function signIn(email, password) {
    setAuthError('')
    try {
      const response = await fetch('/api/auth/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'include',
        body: JSON.stringify({ email, password }),
      })
      if (!response.ok) {
        let body = {}
        try { body = await response.json() } catch { /* Response may not be JSON */ }
        if (response.status === 502 || response.status === 503 || response.status === 504) {
          throw new Error('Backend service is starting up or unavailable. Please wait a moment and try again.')
        }
        if (response.status === 403) {
          throw new Error(body.message || 'Access was restricted. Please try again.')
        }
        throw new Error(body.message || (response.status === 401 ? 'Email or password is incorrect. Try again.' : `Sign in failed (${response.status}).`))
      }
      setSession(await response.json())
      navigate('/')
    } catch (error) {
      setAuthError(error.message)
      throw error
    }
  }

  async function signUp(email, password) {
    setAuthError('')
    try {
      const response = await fetch('/api/auth/register', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'include',
        body: JSON.stringify({ email, password }),
      })
      if (!response.ok) {
        let body = {}
        try { body = await response.json() } catch { /* Response may not be JSON */ }
        if (response.status === 502 || response.status === 503 || response.status === 504) {
          throw new Error('Backend service is starting up or unavailable. Please wait a moment and try again.')
        }
        if (response.status === 403) {
          throw new Error(body.message || 'Access was restricted or session check failed. Please try again.')
        }
        throw new Error(body.message || `Account registration could not be completed (${response.status}).`)
      }
      setSession(await response.json())
      navigate('/')
    } catch (error) {
      setAuthError(error.message)
      throw error
    }
  }

  async function signOut() {
    try { await request('/api/auth/logout', { method: 'POST' }) } catch { /* Access token may have expired. */ }
    setSession(null)
    navigate('/login')
  }

  if (initializing) return <div className="boot-screen"><span className="brand-mark">L</span><span>Opening LedgerLine…</span></div>
  if (!session) {
    return <LoginScreen onLogin={signIn} onRegister={signUp} error={authError} runtimeEnvironment={runtimeEnvironment} />
  }

  const role = session.user?.role
  const allowed = navigation.filter((item) => (!item.role || item.role === role)
    && (!item.roles || item.roles.includes(role)))

  return (
    <ApiContext.Provider value={api}>
      <div className="app-shell">
        <aside className="sidebar">
          <Link className="brand" to="/"><span className="brand-mark">L</span><span>LedgerLine<small>FINANCIAL OPERATIONS</small></span></Link>
          <div className="workspace-label">WORKSPACE</div>
          <nav className="main-nav">
            {allowed.map((item) => <Link key={item.path} to={item.path}
              className={`nav-link ${location.pathname === item.path ? 'active' : ''}`}>
              <span className="nav-icon">{item.icon}</span>{item.label}
              {item.path === '/authorisation' && role === 'AUTHORISER' && <span className="nav-dot" />}
            </Link>)}
          </nav>
          <div className="sidebar-bottom">
            <div className="secure-label"><span className="secure-dot" /> SECURE ENVIRONMENT</div>
            <div className="profile-chip"><span className="avatar">{initials(session.user?.email)}</span>
              <span className="profile-copy">{session.user?.email}<small>{role?.replace('_', ' ')}</small></span>
              <button className="icon-button" title="Sign out" onClick={signOut}>↗</button>
            </div>
          </div>
        </aside>
        <main className="main-content">
          <header className="topbar"><div className="breadcrumb">LedgerLine <span>/</span> {allowed.find((item) => item.path === location.pathname)?.label || 'Page'}</div>
            <div className="topbar-right"><span className="badge gray">{runtimeEnvironment}</span><span className="live-dot" /> API connected <span className="topbar-divider" /> <span className="date-label">{new Date().toLocaleDateString(undefined, { weekday: 'short', month: 'short', day: 'numeric' })}</span></div>
          </header>
          <div className="page-wrap">
            <Routes>
              <Route path="/" element={<Overview />} />
              <Route path="/transactions" element={<Transactions />} />
              <Route path="/ledger" element={<Ledger />} />
              <Route path="/authorisation" element={<Authorisation />} />
              <Route path="/audit" element={role === 'ADMIN' || role === 'AUDITOR' ? <Audit /> : <Navigate to="/" replace />} />
              <Route path="/settings" element={<Settings />} />
              <Route path="*" element={<Navigate to="/" replace />} />
            </Routes>
          </div>
        </main>
      </div>
    </ApiContext.Provider>
  )
}

function LoginScreen({ onLogin, onRegister, error, runtimeEnvironment }) {
  const [registering, setRegistering] = useState(false)
  const [email, setEmail] = useState('holder@ledgerline.demo')
  const [password, setPassword] = useState('LedgerLine-Holder-2026!')
  const [busy, setBusy] = useState(false)
  async function submit(event) {
    event.preventDefault()
    setBusy(true)
    try { await (registering ? onRegister(email, password) : onLogin(email, password)) } catch { /* Error is displayed from the parent. */ }
    finally { setBusy(false) }
  }
  return <div className="login-layout">
    <div className="login-aside"><div className="brand brand-light"><span className="brand-mark">L</span><span>LedgerLine</span></div>
      <div className="login-message"><div className="eyebrow light-eyebrow">FINANCIAL INTEGRITY, IN VIEW</div>
        <h1>Every movement<br />has a record.</h1><p>A transparent ledger for transactions you can verify, trace, and trust.</p></div>
      <div className="login-foot">A FINANCIAL INTEGRITY PROTOTYPE</div>
    </div>
    <div className="login-form-side"><form className="login-card" onSubmit={submit}>
      <div className="eyebrow">{registering ? 'NEW ACCOUNT' : 'WELCOME BACK'}</div><h2>{registering ? 'Create an account' : 'Sign in to your workspace'}</h2>
      <p className="muted">{registering ? 'New accounts are created with the account-holder role.' : 'Use one of the demo roles to explore LedgerLine.'}</p>
      {runtimeEnvironment === 'STARTING_UP' && (
        <div className="login-hint" style={{ color: '#d97706', marginBottom: '0.75rem', background: '#fef3c7', padding: '0.5rem 0.75rem', borderRadius: '4px' }}>
          ⏳ Connecting to backend service (cloud free-tier instances may take ~30-60s to wake up)…
        </div>
      )}
      <label>Email address<input type="email" autoComplete="username" required value={email} onChange={(event) => setEmail(event.target.value)} /></label>
      <label>Password{registering ? ' (min 12 chars)' : ''}<input type="password" autoComplete={registering ? 'new-password' : 'current-password'} minLength={registering ? 12 : undefined} maxLength="72" required value={password} onChange={(event) => setPassword(event.target.value)} /></label>
      {error && <div className="inline-error" role="alert">{error}</div>}
      <button className="primary-button wide-button" disabled={busy}>{busy ? 'Please wait…' : registering ? 'Create account' : 'Sign in'} <span>→</span></button>
      <button type="button" className="text-button login-toggle" onClick={() => setRegistering(!registering)}>{registering ? 'Already have a profile? Sign in' : 'New here? Create an account'}</button>
      <div className="login-hint"><span className="lock-icon">⌑</span> Refresh credentials are stored in an HTTP-only cookie.</div>
    </form></div>
  </div>
}

function useResource(path, enabled = true) {
  const { request } = useApi()
  const [data, setData] = useState(null)
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(true)
  const reload = useCallback(() => {
    if (!enabled) {
      setLoading(false)
      setError('')
      setData(null)
      return Promise.resolve()
    }
    setLoading(true); setError('')
    return request(path).then(setData).catch((failure) => setError(failure.message)).finally(() => setLoading(false))
    // The path is the resource identity; callers pass stable, serializable path strings.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [request, path, enabled])
  useEffect(() => { reload() }, [reload])
  return { data, error, loading, reload, setData }
}

function PageTitle({ eyebrow, title, description, action }) {
  return <div className="page-title-row"><div><div className="eyebrow">{eyebrow}</div><h1>{title}</h1>{description && <p className="page-description">{description}</p>}</div>{action}</div>
}
function ErrorState({ message, retry }) {
  return <div className="error-state"><span className="error-symbol">!</span><div><strong>Couldn’t load this data</strong><p>{message}</p></div>{retry && <button className="text-button" onClick={retry}>Try again</button>}</div>
}
function LoadingRows({ count = 4 }) {
  return <div className="loading-rows" aria-label="Loading">{Array.from({ length: count }, (_, index) => <div key={index} />)}</div>
}
function EmptyState({ title, message }) { return <div className="empty-state"><span>◌</span><strong>{title}</strong><p>{message}</p></div> }
function Panel({ title, subtitle, action, children, className = '' }) {
  return <section className={`panel ${className}`}><div className="panel-heading"><div><h2>{title}</h2>{subtitle && <p>{subtitle}</p>}</div>{action}</div>{children}</section>
}
function Badge({ value }) {
  const tone = ['COMPLETED', 'VERIFIED', 'SUCCESS', 'APPROVED', 'UP'].includes(value) ? 'green'
    : ['PENDING', 'TAMPERED', 'BLOCKED'].includes(value) ? 'amber'
      : ['REJECTED', 'FAILED', 'DENIED', 'DOWN'].includes(value) ? 'red' : 'gray'
  return <span className={`badge ${tone}`}><span />{value?.replaceAll('_', ' ') || '—'}</span>
}
function formatMoney(value) {
  return new Intl.NumberFormat(undefined, { style: 'currency', currency: 'USD', minimumFractionDigits: 2 }).format(Number(value || 0))
}
function formatDate(value) {
  return value ? new Date(value).toLocaleString(undefined, { month: 'short', day: 'numeric', year: 'numeric', hour: '2-digit', minute: '2-digit' }) : '—'
}
function initials(email = '') { return email.split('@')[0].split(/[._-]/).slice(0, 2).map((part) => part[0]?.toUpperCase()).join('') || 'LL' }
function StatCard({ label, value, note, icon, tone = '' }) {
  return <div className={`stat-card ${tone}`}><div className="stat-top"><span>{label}</span><span className="stat-icon">{icon}</span></div><strong>{value}</strong><small>{note}</small></div>
}

function Overview() {
  const { data: stats, error, loading, reload } = useResource('/api/overview')
  const { data: transactions, error: txError, loading: txLoading } = useResource('/api/transactions')
  const { data: accounts, error: accountError, loading: accountLoading } = useResource('/api/accounts')
  const { data: health, error: healthError } = useResource('/api/health')
  const { request, session } = useApi()
  const [integrity, setIntegrity] = useState(null)
  const [verifyError, setVerifyError] = useState('')
  const [checking, setChecking] = useState(false)
  const { data: lastCheck, error: lastCheckError } = useResource('/api/ledger/verification-status')
  const canVerify = ['ADMIN', 'AUDITOR'].includes(session.user?.role)
  async function verify() {
    setChecking(true); setVerifyError('')
    try { setIntegrity(await request('/api/ledger/verify', { method: 'POST' })) }
    catch (failure) { setVerifyError(failure.message) }
    finally { setChecking(false) }
  }
  const recent = (transactions || []).slice(0, 5)
  return <div className="page-stack">
    <PageTitle eyebrow="OVERVIEW" title="Good to see you." description="A clear view of your accounts and the activity behind them." />
    {error && <ErrorState message={error} retry={reload} />}
    {!error && loading ? <LoadingRows count={1} /> : stats && <div className="stats-grid">
      <StatCard label="Total transactions" value={stats.totalTransactions} note="Across your workspace" icon="↔" />
      <StatCard label="Successful" value={stats.successfulTransactions} note="Settled successfully" icon="✓" tone="stat-green" />
      <StatCard label="Failed" value={stats.failedTransactions} note="Rejected or unsuccessful" icon="!" tone="stat-red" />
      <StatCard label="Pending" value={stats.pendingTransactions} note="Awaiting authorisation" icon="◷" tone="stat-amber" />
    </div>}
    <div className="overview-grid">
      <Panel title="Transaction activity" subtitle="Recent requests from your workspace" className="activity-panel">
        {txError ? <ErrorState message={txError} /> : txLoading ? <LoadingRows /> : recent.length ? <div className="activity-content">
          <div className="activity-total"><span>Completed volume</span><strong>{formatMoney(stats?.totalVolume)}</strong><small>Based on completed transactions</small></div>
          <div className="mini-bars" aria-label="Recent real transaction amounts">{recent.map((row, index) => {
            const max = Math.max(...recent.map((item) => Number(item.amount)), 1)
            return <div className="bar-col" key={row.id}><div className={`bar ${row.status === 'COMPLETED' ? '' : 'bar-muted'}`} style={{ height: `${Math.max(8, Number(row.amount) / max * 100)}%` }} title={`${formatMoney(row.amount)} — ${row.status}`} /><small>{new Date(row.createdAt).toLocaleDateString(undefined, { month: 'short', day: 'numeric' })}</small></div>
          })}</div>
        </div> : <EmptyState title="No activity yet" message="Once transactions are created, activity will appear here." />}
      </Panel>
      <Panel title="Security & system health" subtitle="Live service checks">
        <div className="health-list">
          {healthError ? <div className="health-row"><strong>API status unavailable</strong><Badge value="DOWN" /></div>
            : <HealthRow label="API status" value={health?.status || 'DOWN'} description="Live health endpoint" />}
          <div className="health-row"><div><strong>Ledger integrity</strong><small>{integrity ? `Checked ${formatDate(integrity.verifiedAt)}` : lastCheck?.verifiedAt ? `Checked ${formatDate(lastCheck.verifiedAt)}` : 'No verification has run yet'}</small></div>
            {integrity ? <Badge value={integrity.status} /> : lastCheck?.status && lastCheck.status !== 'NOT_RUN'
              ? <Badge value={lastCheck.status} /> : canVerify
                ? <button className="small-button" onClick={verify} disabled={checking}>{checking ? 'Checking…' : 'Verify'}</button>
                : <Badge value="NOT_RUN" />}</div>
          <HealthRow label="Authorisation service" value={healthError ? 'DOWN' : health?.authorisation || 'DOWN'} description="Live API capability check" />
          <HealthRow label="Duplicate protection" value={healthError ? 'DOWN' : health?.duplicateProtection || 'DOWN'} description="Idempotency service capability" />
          {integrity && <div className="hash-preview">Last hash <code>{integrity.lastHash?.slice(0, 18)}…</code></div>}
          {verifyError && <p className="inline-error">{verifyError}</p>}
          {lastCheckError && <p className="inline-error">{lastCheckError}</p>}
        </div>
      </Panel>
    </div>
    <Panel title="Accounts" subtitle="Accounts available to your role">
      {accountError ? <ErrorState message={accountError} /> : accountLoading ? <LoadingRows /> : accounts?.length ? <div className="account-grid">{accounts.map((account) =>
        <div className="account-tile" key={account.id}><div className="account-symbol">◈</div><span className="account-number">{account.accountNumber}</span><strong>{formatMoney(account.balance)}</strong><small><span className="live-dot" /> {account.status}</small></div>)}</div>
        : <EmptyState title="No accounts to show" message="An administrator can create an account for your profile." />}
    </Panel>
    <Panel title="Recent transactions" subtitle="The latest recorded movements" action={<Link className="text-button" to="/transactions">View all →</Link>}>
      {txError ? <ErrorState message={txError} /> : txLoading ? <LoadingRows /> : recent.length ? <TransactionTable rows={recent} compact /> : <EmptyState title="No transactions yet" message="Your transaction history will be available here." />}
    </Panel>
  </div>
}

function TransactionTable({ rows, compact = false, onSelect, sortBy, sortDir, onSort }) {
  const heading = (label, field) => onSort
    ? <button className="table-sort" onClick={() => onSort(field)}>{label}{sortBy === field ? (sortDir === 'asc' ? ' ↑' : ' ↓') : ''}</button>
    : label
  return <div className="table-scroll"><table><thead><tr><th>{heading('Transaction', 'id')}</th><th>{heading('Type', 'type')}</th><th>{heading('Amount', 'amount')}</th><th>Status</th><th>{heading('Date', 'createdAt')}</th></tr></thead>
    <tbody>{rows.map((row) => <tr key={row.id} onClick={() => onSelect?.(row)} className={onSelect ? 'clickable-row' : ''}>
      <td><span className="mono">{row.id.slice(0, compact ? 12 : 16)}</span><small className="cell-sub">{row.memo || '—'}</small></td>
      <td>{row.type}</td><td className="money-cell">{formatMoney(row.amount)}</td><td><Badge value={row.status} /></td><td>{formatDate(row.createdAt)}</td>
    </tr>)}</tbody></table></div>
}

function Transactions() {
  const { request, session } = useApi()
  const { data: allRows, error, loading, reload, setData } = useResource('/api/transactions')
  const { data: accounts, error: accountsError } = useResource('/api/accounts')
  const [search, setSearch] = useState('')
  const [status, setStatus] = useState('')
  const [type, setType] = useState('')
  const [fromDate, setFromDate] = useState('')
  const [toDate, setToDate] = useState('')
  const [sortBy, setSortBy] = useState('createdAt')
  const [sortDir, setSortDir] = useState('desc')
  const [page, setPage] = useState(0)
  const [selected, setSelected] = useState(null)
  const [notice, setNotice] = useState('')
  const [formError, setFormError] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [reverseReason, setReverseReason] = useState('')
  const [reverseFailure, setReverseFailure] = useState('')
  const [idempotencyKey, setIdempotencyKey] = useState(() => crypto.randomUUID())
  const [form, setForm] = useState({ type: 'TRANSFER', senderAccountId: '', receiverAccountId: '', amount: '', memo: '' })
  const holder = session.user?.role === 'ACCOUNT_HOLDER'
  const filtered = (allRows || []).filter((row) => (!search || row.id.toLowerCase().includes(search.toLowerCase()))
    && (!status || row.status === status) && (!type || row.type === type)
    && (!fromDate || row.createdAt.slice(0, 10) >= fromDate)
    && (!toDate || row.createdAt.slice(0, 10) <= toDate))
    .sort((left, right) => {
      const a = left[sortBy]
      const b = right[sortBy]
      const comparison = sortBy === 'amount' ? Number(a) - Number(b) : String(a ?? '').localeCompare(String(b ?? ''))
      return sortDir === 'asc' ? comparison : -comparison
    })
  const pageSize = 10
  const rows = filtered.slice(page * pageSize, (page + 1) * pageSize)
  async function submit(event) {
    event.preventDefault(); setFormError(''); setNotice('')
    setSubmitting(true)
    try {
      if (!/^(?:\d{1,17})(?:\.\d{1,2})?$/.test(form.amount) || Number(form.amount) <= 0) {
        throw new Error('Enter a positive amount with no more than two decimal places.')
      }
      async function reverseTransaction() {
        setReverseFailure('')
        try {
          const reversed = await request(`/api/transactions/${selected.id}/reverse`, {
            method: 'POST',
            headers: { 'Idempotency-Key': crypto.randomUUID() },
            body: JSON.stringify({ reason: reverseReason }),
          })
          setData([reversed, ...(allRows || [])].map((row) =>
            row.id === selected.id ? { ...row, status: 'REVERSED' } : row))
          setSelected(reversed)
          setReverseReason('')
        } catch (failure) { setReverseFailure(failure.message) }
      }
      const body = { type: form.type, amount: form.amount, memo: form.memo }
      if (form.type !== 'DEPOSIT') body.senderAccountId = Number(form.senderAccountId)
      if (form.type !== 'WITHDRAWAL') body.receiverAccountId = Number(form.receiverAccountId)
      const response = await request('/api/transactions', {
        method: 'POST', headers: { 'Idempotency-Key': idempotencyKey }, body: JSON.stringify(body),
      })
      setData([response, ...(allRows || [])]); setNotice(`Transaction ${response.id.slice(0, 8)} created as ${response.status}.`)
      setIdempotencyKey(crypto.randomUUID())
    } catch (failure) { setFormError(failure.message) }
    finally { setSubmitting(false) }
  }
  function updateForm(values) {
    setForm({ ...form, ...values })
    setIdempotencyKey(crypto.randomUUID())
  }
  return <div className="page-stack">
    <PageTitle eyebrow="ACTIVITY" title="Transactions" description="Search, review, and initiate account movements." />
    {holder && <Panel title="New transaction" subtitle="Requests above the approval threshold wait for authorisation.">
      {accountsError && <ErrorState message={accountsError} />}
      {formError && <div className="inline-error">{formError}</div>}{notice && <div className="inline-success">{notice}</div>}
      <form className="transaction-form" onSubmit={submit}>
        <label>Movement type<select value={form.type} onChange={(event) => updateForm({ type: event.target.value })}><option>TRANSFER</option><option>DEPOSIT</option><option>WITHDRAWAL</option></select></label>
        {form.type !== 'DEPOSIT' && <label>From account<select required value={form.senderAccountId} onChange={(event) => updateForm({ senderAccountId: event.target.value })}><option value="">Select account</option>{(accounts || []).map((a) => <option key={a.id} value={a.id}>{a.accountNumber} · {formatMoney(a.balance)}</option>)}</select></label>}
        {form.type !== 'WITHDRAWAL' && <label>To account<select required value={form.receiverAccountId} onChange={(event) => updateForm({ receiverAccountId: event.target.value })}><option value="">Select account</option>{(accounts || []).map((a) => <option key={a.id} value={a.id}>{a.accountNumber}</option>)}</select></label>}
        <label>Amount<input required type="number" min="0.01" step="0.01" value={form.amount} onChange={(event) => updateForm({ amount: event.target.value })} placeholder="0.00" /></label>
        <label>Memo<input maxLength="140" value={form.memo} onChange={(event) => updateForm({ memo: event.target.value })} placeholder="Optional reference" /></label>
        <button className="primary-button" disabled={submitting}>{submitting ? 'Submitting…' : 'Submit request'} <span>→</span></button>
      </form>
    </Panel>}
    <Panel title="All transactions" subtitle={`${filtered.length} recorded movements`}>
      <div className="filters"><label className="search-field"><span>⌕</span><input value={search} onChange={(event) => { setSearch(event.target.value); setPage(0) }} placeholder="Search by transaction ID" /></label>
        <select value={status} onChange={(event) => setStatus(event.target.value)}><option value="">All statuses</option>{['COMPLETED', 'PENDING', 'REJECTED', 'FAILED'].map((value) => <option key={value}>{value}</option>)}</select>
        <select value={type} onChange={(event) => setType(event.target.value)}><option value="">All types</option>{['DEPOSIT', 'WITHDRAWAL', 'TRANSFER', 'REVERSAL'].map((value) => <option key={value}>{value}</option>)}</select>
        <input aria-label="From date" type="date" value={fromDate} onChange={(event) => setFromDate(event.target.value)} />
        <input aria-label="To date" type="date" value={toDate} onChange={(event) => setToDate(event.target.value)} />
      </div>
      {error ? <ErrorState message={error} retry={reload} /> : loading ? <LoadingRows /> : rows.length ? <TransactionTable rows={rows} onSelect={setSelected} sortBy={sortBy} sortDir={sortDir} onSort={(field) => { setSortDir(sortBy === field && sortDir === 'desc' ? 'asc' : 'desc'); setSortBy(field) }} /> : <EmptyState title="No matching transactions" message="Try adjusting your filters or create a new transaction." />}
      <div className="pagination"><span>Page {filtered.length ? page + 1 : 0} of {Math.max(1, Math.ceil(filtered.length / pageSize))}</span><div><button className="small-button" disabled={!page} onClick={() => setPage(page - 1)}>Previous</button><button className="small-button" disabled={(page + 1) * pageSize >= filtered.length} onClick={() => setPage(page + 1)}>Next</button></div></div>
    </Panel>
    {selected && <div className="drawer-backdrop" onClick={() => setSelected(null)}><aside className="details-drawer" onClick={(event) => event.stopPropagation()}>
      <div className="drawer-head"><div><div className="eyebrow">TRANSACTION DETAILS</div><h2>Movement record</h2></div><button className="icon-button" onClick={() => setSelected(null)}>×</button></div>
      <dl className="detail-list">{Object.entries({ ID: selected.id, Type: selected.type, Status: selected.status, Amount: formatMoney(selected.amount), Source: selected.senderAccountId || 'System float', Destination: selected.receiverAccountId || 'System float', Memo: selected.memo || '—', Created: formatDate(selected.createdAt) }).map(([key, value]) => <div key={key}><dt>{key}</dt><dd>{value}</dd></div>)}</dl>
      {selected.reversalOfTransactionId && <p className="cell-sub">Compensates transaction {selected.reversalOfTransactionId}</p>}
      {['ADMIN', 'AUTHORISER'].includes(session.user?.role) && selected.status === 'COMPLETED'
        && selected.type !== 'REVERSAL' && <div className="reverse-form">
          {reverseFailure && <div className="inline-error">{reverseFailure}</div>}
          <label>Reversal reason<input required maxLength="140" value={reverseReason}
            onChange={(event) => setReverseReason(event.target.value)} /></label>
          <button className="small-button reject-button" disabled={!reverseReason.trim()}
            onClick={reverseTransaction}>Create compensating reversal</button>
        </div>}
    </aside></div>}
  </div>
}

function Ledger() {
  const { data, error, loading, reload } = useResource('/api/ledger')
  const { request, session } = useApi()
  const { data: accounts, error: accountError } = useResource('/api/accounts')
  const [result, setResult] = useState(null)
  const [failure, setFailure] = useState('')
  const [checking, setChecking] = useState(false)
  const [accountId, setAccountId] = useState('')
  const [fromDate, setFromDate] = useState('')
  const [toDate, setToDate] = useState('')
  async function verify() {
    setChecking(true); setFailure('')
    try { setResult(await request('/api/ledger/verify', { method: 'POST' })) }
    catch (err) { setFailure(err.message) }
    finally { setChecking(false) }
  }
  async function downloadStatement() {
    setFailure('')
    try {
      const query = new URLSearchParams()
      if (fromDate) query.set('from', fromDate)
      if (toDate) query.set('to', toDate)
      const queryString = query.toString()
      const suffix = queryString ? `?${queryString}` : ''
      const blob = await request(`/api/accounts/${accountId}/statement.csv${suffix}`)
      const url = URL.createObjectURL(blob)
      const link = document.createElement('a')
      link.href = url
      link.download = `ledgerline-statement-${accountId}.csv`
      link.click()
      setTimeout(() => URL.revokeObjectURL(url), 1000)
    } catch (err) { setFailure(err.message) }
  }
  return <div className="page-stack">
    <PageTitle eyebrow="RECORDS" title="Ledger" description="Debit and credit entries with running balances and a verifiable hash chain."
      action={['ADMIN', 'AUDITOR'].includes(session.user?.role) && <button className="primary-button" onClick={verify} disabled={checking}>{checking ? 'Verifying…' : 'Verify chain'} <span>↻</span></button>} />
    {failure && <div className="inline-error">{failure}</div>}
    {result && <div className={`verification-banner ${result.status === 'VERIFIED' ? 'verified' : 'tampered'}`}>
      <span className="verification-symbol">{result.status === 'VERIFIED' ? '✓' : '!'}</span><div><strong>{result.status === 'VERIFIED' ? 'Ledger verified' : `Integrity issue at row ${result.brokenRowId}`}</strong>
        <p>{result.checkedRows} entries checked · {formatDate(result.verifiedAt)}</p><code>{result.lastHash}</code></div><Badge value={result.status} /></div>}
    <Panel title="Ledger entries" subtitle={`${data?.length || 0} most recent entries`}>
      <div className="filters statement-filters">
        {accountError ? <span className="inline-error">{accountError}</span>
          : <select aria-label="Statement account" required value={accountId}
            onChange={(event) => setAccountId(event.target.value)}>
            <option value="">Select account for CSV statement</option>
            {(accounts || []).map((account) => <option key={account.id} value={account.id}>
              {account.accountNumber}
            </option>)}
          </select>}
        <input aria-label="Statement from date" type="date" value={fromDate}
          onChange={(event) => setFromDate(event.target.value)} />
        <input aria-label="Statement to date" type="date" value={toDate}
          onChange={(event) => setToDate(event.target.value)} />
        <button className="small-button" disabled={!accountId} onClick={downloadStatement}>
          Download CSV statement
        </button>
      </div>
      {error ? <ErrorState message={error} retry={reload} /> : loading ? <LoadingRows /> : data?.length ? <div className="table-scroll"><table><thead><tr><th>Entry</th><th>Account</th><th>Transaction</th><th>Direction</th><th>Amount</th><th>Running balance</th><th>Recorded</th></tr></thead>
        <tbody>{data.map((row) => <tr key={row.id}><td className="mono">#{row.id}</td><td className="mono">{row.accountId}</td><td className="mono">{row.transactionId.slice(0, 12)}</td><td><Badge value={row.entryType} /></td><td>{formatMoney(row.amount)}</td><td className="money-cell">{formatMoney(row.runningBalance)}</td><td>{formatDate(row.createdAt)}</td></tr>)}</tbody></table></div>
        : <EmptyState title="No ledger entries" message="Recorded money movements will appear here." />}
    </Panel>
    <div className="notice-card"><span>i</span><p>The chain is a SHA-256 hash chain, not a blockchain. Verification checks each stored record against its predecessor and reports the first break.</p></div>
  </div>
}

function Authorisation() {
  const { request, session } = useApi()
  const { data, error, loading, reload, setData } = useResource('/api/authorisations')
  const [actionError, setActionError] = useState('')
  const [filter, setFilter] = useState(session.user?.role === 'AUTHORISER' ? 'PENDING' : 'ALL')
  async function decide(row, action) {
    setActionError('')
    try {
      const updated = await request(`/api/authorisations/${row.id}/${action}`, { method: 'POST' })
      setData((data || []).map((item) => item.id === row.id ? updated : item))
    } catch (failure) { setActionError(failure.message) }
  }
  const approver = session.user?.role === 'AUTHORISER'
  const approved = (data || []).filter((row) => row.status === 'COMPLETED' && row.decidedByUserId)
  const rejected = (data || []).filter((row) => row.status === 'REJECTED')
  const pending = (data || []).filter((row) => row.status === 'PENDING')
  const visible = filter === 'PENDING' ? pending : filter === 'APPROVED' ? approved
    : filter === 'REJECTED' ? rejected : [...pending, ...approved, ...rejected]
  return <div className="page-stack">
    <PageTitle eyebrow="CONTROL" title="Authorisation queue" description="Review high-value movements before settlement." />
    {actionError && <div className="inline-error">{actionError}</div>}
    <Panel title={approver ? 'Authorisation decisions' : 'Authorisation history'} subtitle={approver ? 'Approve or reject pending high-value requests.' : 'Read-only transaction history for your role.'}>
      <div className="segmented-tabs">{[['ALL', 'All', pending.length + approved.length + rejected.length], ['PENDING', 'Pending', pending.length], ['APPROVED', 'Approved', approved.length], ['REJECTED', 'Rejected', rejected.length]].map(([key, label, count]) =>
        <button key={key} className={filter === key ? 'selected' : ''} onClick={() => setFilter(key)}>{label}<span>{count}</span></button>)}</div>
      {error ? <ErrorState message={error} retry={reload} /> : loading ? <LoadingRows /> : visible.length ? <div className="table-scroll"><table><thead><tr><th>Transaction</th><th>Type</th><th>Amount</th><th>Status</th><th>Requested</th>{approver && <th>Decision</th>}</tr></thead>
        <tbody>{visible.map((row) => <tr key={row.id}><td className="mono">{row.id.slice(0, 16)}</td><td>{row.type}</td><td>{formatMoney(row.amount)}</td><td><Badge value={row.status === 'COMPLETED' && row.decidedByUserId ? 'APPROVED' : row.status} /></td><td>{formatDate(row.createdAt)}</td>
          {approver && <td className="action-cell">{row.status === 'PENDING' ? <><button className="small-button approve-button" onClick={() => decide(row, 'approve')}>Approve</button><button className="small-button reject-button" onClick={() => decide(row, 'reject')}>Reject</button></> : '—'}</td>}</tr>)}</tbody></table></div>
        : <EmptyState title={filter === 'PENDING' ? 'Queue is clear' : 'No matching decisions'} message={filter === 'PENDING' ? 'There are no pending requests to review.' : 'No authorisation history is available for this filter.'} />}
    </Panel>
  </div>
}

function Audit() {
  const { session } = useApi()
  const { data: audit, error: auditError, loading: auditLoading, reload: reloadAudit } = useResource('/api/audit')
  const { data: security, error: securityError, loading: securityLoading, reload: reloadSecurity } = useResource('/api/security-events')
  const failed = auditError || securityError
  const loading = auditLoading || securityLoading
  const rows = [...(audit || []).map((row) => ({ ...row, source: 'AUDIT' })),
    ...(security || []).map((row) => ({ ...row, source: 'SECURITY' }))].sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt))
  return <div className="page-stack">
    <PageTitle eyebrow="GOVERNANCE" title="Audit & security" description="Timestamped audit entries and authentication/security signals." />
    <div className="audit-summary"><div><span>Audit history</span><strong>{audit?.length ?? '—'}</strong></div><div><span>Security events</span><strong>{security?.length ?? '—'}</strong></div><div><span>Audit chain</span><strong className="chain-word">Hash chained</strong></div></div>
    {session.user?.role === 'ADMIN' && <AccountSecurityControls />}
    <Panel title="Event history" subtitle="Most recent audit and security events">
      {failed ? <ErrorState message={failed} retry={() => { reloadAudit(); reloadSecurity() }} /> : loading ? <LoadingRows /> : rows.length ? <div className="table-scroll"><table><thead><tr><th>Event</th><th>Source</th><th>Description</th><th>Status</th><th>Correlation ID</th><th>IP address</th><th>Timestamp</th></tr></thead>
        <tbody>{rows.map((row) => <tr key={`${row.source}-${row.id}`}><td className="mono">{row.eventType}</td><td>{row.source}</td><td className="description-cell">{row.description}</td><td><Badge value={row.status} /></td><td className="mono">{row.correlationId || '—'}</td><td className="mono">{row.ipAddress || '—'}</td><td>{formatDate(row.createdAt)}</td></tr>)}</tbody></table></div>
        : <EmptyState title="No events recorded" message="Security and audit events will appear here as activity occurs." />}
    </Panel>
    <div className="notice-card"><span>i</span><p>*The app writes a SHA-256 chain for audit records. Database-level update/delete restrictions require a separately provisioned runtime DB role; see THREATS.md.</p></div>
  </div>
}

function AccountSecurityControls() {
  const { request } = useApi()
  const { data: accounts, error, loading, reload, setData } = useResource('/api/accounts')
  const [failure, setFailure] = useState('')
  const [notice, setNotice] = useState('')
  async function changeFreeze(account) {
    let reason
    if (account.status !== 'FROZEN') {
      reason = window.prompt(`Why are you freezing ${account.accountNumber}?`)
      if (reason === null) return
      if (!reason.trim()) { setFailure('A reason is required to freeze an account.'); return }
    }
    setFailure(''); setNotice('')
    try {
      const updated = await request(`/api/accounts/${account.id}/${account.status === 'FROZEN' ? 'unfreeze' : 'freeze'}`, {
        method: 'POST',
        body: JSON.stringify(account.status === 'FROZEN' ? {} : { reason: reason.trim() }),
      })
      setData((accounts || []).map((item) => item.id === account.id ? updated : item))
      setNotice(`Account ${account.accountNumber} is ${updated.status.toLowerCase()}.`)
    } catch (err) { setFailure(err.message) }
  }
  return <Panel title="Account response controls" subtitle="Freeze a suspected compromised account; history remains visible.">
    {failure && <div className="inline-error">{failure}</div>}
    {notice && <div className="inline-success">{notice}</div>}
    {error ? <ErrorState message={error} retry={reload} /> : loading ? <LoadingRows count={1} />
      : <div className="user-list account-admin-list">{(accounts || []).map((account) =>
        <div className="user-row" key={account.id}>
          <div><strong>{account.accountNumber}</strong><small>{formatMoney(account.balance)}</small></div>
          <Badge value={account.status} />
          <button className="small-button" onClick={() => changeFreeze(account)}>
            {account.status === 'FROZEN' ? 'Unfreeze' : 'Freeze'}
          </button>
        </div>)}</div>}
  </Panel>
}

function Settings() {
  const { request, session } = useApi()
  const admin = session.user?.role === 'ADMIN'
  const { data: users, error, loading, reload, setData } = useResource('/api/users', admin)
  const { data: adminAccounts, error: accountError, loading: accountsLoading, reload: reloadAccounts, setData: setAccounts } = useResource('/api/accounts', admin)
  const [form, setForm] = useState({ email: '', password: '', role: 'ACCOUNT_HOLDER' })
  const [message, setMessage] = useState('')
  const [failure, setFailure] = useState('')
  const [accountOwner, setAccountOwner] = useState('')
  async function createUser(event) {
    event.preventDefault(); setFailure(''); setMessage('')
    try {
      const user = await request('/api/users', { method: 'POST', body: JSON.stringify(form) })
      setData([...(users || []), user]); setForm({ email: '', password: '', role: 'ACCOUNT_HOLDER' }); setMessage('User created.')
    } catch (err) { setFailure(err.message) }
  }
  async function updateRole(user, role) {
    setFailure(''); setMessage('')
    try {
      const updated = await request(`/api/users/${user.id}/role`, {
        method: 'PUT', body: JSON.stringify({ role }),
      })
      setData((users || []).map((item) => item.id === user.id ? updated : item))
      setMessage(`Role updated for ${user.email}.`)
    } catch (err) { setFailure(err.message) }
  }
  async function createAccount(event) {
    event.preventDefault(); setFailure(''); setMessage('')
    try {
      const account = await request('/api/accounts', {
        method: 'POST', body: JSON.stringify({ ownerUserId: Number(accountOwner) }),
      })
      setAccounts([...(adminAccounts || []), account]); setMessage(`Account ${account.accountNumber} created.`)
    } catch (err) { setFailure(err.message) }
  }
  return <div className="page-stack">
    <PageTitle eyebrow="PREFERENCES" title="Settings" description="Your profile and workspace access." />
    <Panel title="Profile" subtitle="Authenticated identity and assigned role">
      <div className="settings-profile"><span className="avatar large-avatar">{initials(session.user?.email)}</span><div><strong>{session.user?.email}</strong><small>Signed in as <Badge value={session.user?.role} /></small></div></div>
    </Panel>
    {admin && <Panel title="User management" subtitle="Create workspace users with a server-enforced role.">
      {failure && <div className="inline-error">{failure}</div>}{message && <div className="inline-success">{message}</div>}
      <form className="user-form" onSubmit={createUser}><label>Email<input required type="email" maxLength="254" value={form.email} onChange={(event) => setForm({ ...form, email: event.target.value })} /></label>
        <label>Temporary password<input required type="password" minLength="12" maxLength="72" value={form.password} onChange={(event) => setForm({ ...form, password: event.target.value })} /></label>
        <label>Role<select value={form.role} onChange={(event) => setForm({ ...form, role: event.target.value })}>{['ADMIN', 'AUTHORISER', 'ACCOUNT_HOLDER', 'AUDITOR'].map((role) => <option key={role}>{role}</option>)}</select></label>
        <button className="primary-button">Create user <span>+</span></button></form>
      {error ? <ErrorState message={error} retry={reload} /> : loading ? <LoadingRows /> : <div className="user-list">{(users || []).map((user) => <div className="user-row" key={user.id}><span className="avatar">{initials(user.email)}</span><div><strong>{user.email}</strong><small>User #{user.id}</small></div><select aria-label={`Role for ${user.email}`} value={user.role} onChange={(event) => updateRole(user, event.target.value)}>{['ADMIN', 'AUTHORISER', 'ACCOUNT_HOLDER', 'AUDITOR'].map((role) => <option key={role}>{role}</option>)}</select></div>)}</div>}
      <div className="settings-subheading"><strong>Create account</strong><small>New accounts start at a zero balance.</small></div>
      {accountError ? <ErrorState message={accountError} retry={reloadAccounts} /> : accountsLoading ? <LoadingRows count={1} /> : <form className="user-form" onSubmit={createAccount}>
        <label>Account owner<select required value={accountOwner} onChange={(event) => setAccountOwner(event.target.value)}><option value="">Select user</option>{(users || []).filter((user) => user.role === 'ACCOUNT_HOLDER').map((user) => <option key={user.id} value={user.id}>{user.email}</option>)}</select></label>
        <button className="primary-button">Create account <span>+</span></button>
      </form>}
    </Panel>}
  </div>
}

function HealthRow({ label, value, description }) {
  return <div className="health-row"><div><strong>{label}</strong><small>{description}</small></div><Badge value={value} /></div>
}

export default App
