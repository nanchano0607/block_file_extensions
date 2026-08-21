import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { policyApi } from './api/policyApi'
import { uploadApi } from './api/uploadApi'
import './App.css'

const MAX_FILE_SIZE = 10 * 1024 * 1024
const HISTORY_PAGE_SIZE = 10
const SERVER_ERROR_MESSAGE = '일시적인 오류가 발생했습니다. 잠시 후 다시 시도해주세요.'

const HISTORY_ACTION_LABELS = {
  BLOCK_ON: '고정 차단 활성화',
  BLOCK_OFF: '고정 차단 비활성화',
  ADD: '커스텀 추가',
  DELETE: '커스텀 삭제',
}

function formatBytes(bytes) {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}

function candidatesOf(filename) {
  if (!filename.includes('.')) return []
  return filename.split('.').map((part) => part.trim().toLowerCase()).filter(Boolean)
}

function normalizeCustomExtension(value) {
  const trimmed = value.trim()
  return (trimmed.startsWith('.') ? trimmed.slice(1) : trimmed).toLowerCase()
}

function formatHistoryTime(value) {
  if (!value) return '-'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return new Intl.DateTimeFormat('ko-KR', {
    year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit', second: '2-digit',
  }).format(date)
}

function Spinner({ small = false }) {
  return <span className={`spinner${small ? ' spinner--small' : ''}`} aria-hidden="true" />
}

function PolicyPanel({ onPolicyChange, onHistoryChange }) {
  const [fixed, setFixed] = useState([])
  const [custom, setCustom] = useState({ count: 0, limit: 200, items: [] })
  const [input, setInput] = useState('')
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(null)
  const [adding, setAdding] = useState(false)
  const [deleting, setDeleting] = useState(null)
  const [loadError, setLoadError] = useState('')
  const [fixedError, setFixedError] = useState('')
  const [formError, setFormError] = useState('')

  const load = useCallback(async () => {
    setLoading(true)
    setLoadError('')
    try {
      const [fixedData, customData] = await Promise.all([
        policyApi.getFixed(),
        policyApi.getCustom(),
      ])
      setFixed(fixedData)
      setCustom(customData)
    } catch {
      setLoadError('정책을 불러오지 못했습니다.')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    load()
  }, [load])

  // fixed/custom 중 어느 쪽이 바뀌었든 이 effect가 항상 최신 커밋된 상태로 실행되므로,
  // 각 변경 핸들러가 서로 상대방 도메인의 값을 클로저로 직접 넘길 때 생기던
  // stale-closure 레이스(동시에 정책을 여러 개 바꾸면 blockedExtensions가 어긋나는 문제)가 없다.
  useEffect(() => {
    onPolicyChange?.(fixed, custom.items)
  }, [fixed, custom, onPolicyChange])

  const toggleFixed = async (item) => {
    const nextBlocked = !item.blocked
    setFixedError('')
    setSaving(item.extension)
    setFixed((items) => items.map((value) =>
      value.extension === item.extension ? { ...value, blocked: nextBlocked } : value,
    ))
    try {
      await policyApi.updateFixed(item.extension, nextBlocked)
      onHistoryChange?.()
    } catch (error) {
      setFixed((items) => items.map((value) =>
        value.extension === item.extension ? item : value,
      ))
      setFixedError(error.message || '저장에 실패했습니다.')
    } finally {
      setSaving(null)
    }
  }

  const addCustom = async (event) => {
    event.preventDefault()
    setFormError('')
    const normalized = normalizeCustomExtension(input)
    if (!normalized || normalized.length > 20) {
      setFormError('확장자는 1~20자로 입력해주세요.')
      return
    }
    if (!/^[a-z0-9]+$/.test(normalized)) {
      setFormError('영문/숫자만 입력 가능합니다.')
      return
    }
    setAdding(true)
    try {
      const created = await policyApi.addCustom(normalized)
      setCustom((current) => ({ ...current, count: current.count + 1, items: [...current.items, created] }))
      setInput('')
      onHistoryChange?.()
    } catch (error) {
      setFormError(error.message || '일시적인 오류가 발생했습니다. 잠시 후 다시 시도해주세요.')
    } finally {
      setAdding(false)
    }
  }

  const deleteCustom = async (item) => {
    setFormError('')
    setDeleting(item.id)
    setCustom((current) => ({
      ...current,
      count: current.count - 1,
      items: current.items.filter((value) => value.id !== item.id),
    }))
    try {
      await policyApi.deleteCustom(item.id)
      onHistoryChange?.()
    } catch (error) {
      // 삭제 시도 이전 전체 스냅샷이 아니라, 현재 상태에 이 항목만 되돌려놓는다 —
      // 그래야 이 요청이 실패하는 동안 다른 항목이 성공적으로 삭제/추가됐어도 덮어쓰지 않는다.
      setCustom((current) => ({
        ...current,
        count: current.count + 1,
        items: [...current.items, item],
      }))
      setFormError(error.message || '삭제에 실패했습니다.')
    } finally {
      setDeleting(null)
    }
  }

  if (loading) {
    return <section id="policy" className="panel panel--center"><Spinner /><p>차단 정책을 불러오는 중입니다.</p></section>
  }

  if (loadError) {
    return (
      <section id="policy" className="panel panel--center">
        <div className="status-icon status-icon--error">!</div>
        <p>{loadError}</p>
        <button className="button button--secondary" onClick={load}>다시 시도</button>
      </section>
    )
  }

  const atLimit = custom.count >= custom.limit

  return (
    <section id="policy" className="panel" aria-labelledby="policy-title">
      <div className="panel-heading">
        <div>
          <span className="eyebrow">POLICY</span>
          <h2 id="policy-title">파일 확장자 차단</h2>
          <p>등록한 정책은 파일 업로드 시 서버에서 최종 적용됩니다.</p>
        </div>
        <span className="save-state"><span className="save-dot" /> 자동 저장</span>
      </div>

      <div className="policy-section">
        <div className="section-label">
          <span className="section-number">01</span>
          <div><h3>고정 확장자</h3><p>자주 차단하는 실행 파일 형식입니다.</p></div>
        </div>
        <div className="fixed-grid">
          {fixed.map((item) => (
            <label className={`check-card${item.blocked ? ' check-card--active' : ''}`} key={item.extension}>
              <input type="checkbox" checked={item.blocked} disabled={saving !== null} onChange={() => toggleFixed(item)} />
              <span className="custom-check">{item.blocked ? '✓' : ''}</span>
              <span>.{item.extension}</span>
              {saving === item.extension && <Spinner small />}
            </label>
          ))}
        </div>
        {fixedError && <p className="inline-error" role="alert">{fixedError}</p>}
      </div>

      <div className="divider" />

      <div className="policy-section">
        <div className="section-label section-label--row">
          <span className="section-number">02</span>
          <div><h3>커스텀 확장자</h3><p>영문과 숫자 조합으로 최대 20자까지 등록할 수 있습니다.</p></div>
          <strong className={atLimit ? 'counter counter--limit' : 'counter'}>{custom.count} / {custom.limit}</strong>
        </div>
        <form className="extension-form" onSubmit={addCustom}>
          <div className="input-wrap">
            <span className="input-prefix">.</span>
            <input aria-label="커스텀 확장자" placeholder="확장자 입력" value={input} maxLength={20} disabled={atLimit || adding}
              onChange={(event) => { setInput(event.target.value.replace(/^\./, '')); setFormError('') }} />
            <span className="character-count">{input.length}/20</span>
          </div>
          <button type="submit" className="button button--primary" disabled={atLimit || adding || !input.trim()}>
            {adding ? <><Spinner small /> 추가 중</> : '추가'}
          </button>
        </form>
        {formError && <p className="inline-error" role="alert">{formError}</p>}
        {atLimit && <p className="inline-error">커스텀 확장자는 최대 200개까지 등록할 수 있습니다.</p>}
        <div className={`chip-box${custom.items.length === 0 ? ' chip-box--empty' : ''}`}>
          {custom.items.length === 0 ? <p>등록된 커스텀 확장자가 없습니다.</p> : custom.items.map((item) => (
            <span className="chip" key={item.id}>
              .{item.extension}
              <button type="button" aria-label={`${item.extension} 확장자 삭제`} disabled={deleting === item.id} onClick={() => deleteCustom(item)}>
                {deleting === item.id ? <Spinner small /> : '×'}
              </button>
            </span>
          ))}
        </div>
      </div>
    </section>
  )
}

function PolicyHistoryPanel() {
  const [page, setPage] = useState(0)
  const [history, setHistory] = useState({
    page: 0,
    size: HISTORY_PAGE_SIZE,
    totalElements: 0,
    totalPages: 0,
    hasPrevious: false,
    hasNext: false,
    items: [],
  })
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [retryKey, setRetryKey] = useState(0)

  useEffect(() => {
    let active = true
    policyApi.getHistory(page, HISTORY_PAGE_SIZE)
      .then((data) => {
        if (active) setHistory(data)
      })
      .catch(() => {
        if (active) setError('정책 변경 이력을 불러오지 못했습니다.')
      })
      .finally(() => {
        if (active) setLoading(false)
      })
    return () => { active = false }
  }, [page, retryKey])

  const movePage = (nextPage) => {
    setLoading(true)
    setError('')
    setPage(nextPage)
  }

  const retry = () => {
    setLoading(true)
    setError('')
    setRetryKey((value) => value + 1)
  }

  return (
    <section id="history" className="panel" aria-labelledby="history-title">
      <div className="panel-heading history-heading">
        <div>
          <span className="eyebrow">AUDIT LOG</span>
          <h2 id="history-title">정책 변경 이력</h2>
          <p>고정·커스텀 확장자 정책의 변경 내역을 최신순으로 확인합니다.</p>
        </div>
        <span className="history-total">총 {history.totalElements}건</span>
      </div>

      {loading && <div className="history-state"><Spinner /><span>이력을 불러오는 중입니다.</span></div>}
      {!loading && error && (
        <div className="history-state history-state--error" role="alert">
          <span>{error}</span>
          <button className="button button--secondary" onClick={retry}>다시 시도</button>
        </div>
      )}
      {!loading && !error && history.items.length === 0 && (
        <div className="history-state">아직 정책 변경 이력이 없습니다.</div>
      )}
      {!loading && !error && history.items.length > 0 && (
        <>
          <div className="history-list">
            {history.items.map((item) => (
              <article className="history-item" key={item.id}>
                <span className={`history-type history-type--${item.policyType.toLowerCase()}`}>
                  {item.policyType === 'FIXED' ? '고정' : '커스텀'}
                </span>
                <strong>.{item.extension}</strong>
                <span className="history-action">{HISTORY_ACTION_LABELS[item.action] || item.action}</span>
                <time dateTime={item.changedAt}>{formatHistoryTime(item.changedAt)}</time>
              </article>
            ))}
          </div>
          <div className="history-pagination" aria-label="정책 변경 이력 페이지">
            <button className="button button--secondary" disabled={!history.hasPrevious} onClick={() => movePage(page - 1)}>이전</button>
            <span>{history.totalPages === 0 ? 0 : history.page + 1} / {history.totalPages}</span>
            <button className="button button--secondary" disabled={!history.hasNext} onClick={() => movePage(page + 1)}>다음</button>
          </div>
        </>
      )}
    </section>
  )
}

function UploadPanel({ blockedExtensions }) {
  const fileInput = useRef(null)
  const [file, setFile] = useState(null)
  const [dragging, setDragging] = useState(false)
  const [state, setState] = useState('idle')
  const [message, setMessage] = useState('')
  const candidates = useMemo(() => file ? candidatesOf(file.name) : [], [file])
  const extensionWarning = candidates.some((value) => blockedExtensions.has(value))
  const sizeRejected = file && file.size > MAX_FILE_SIZE

  const selectFile = (selected) => {
    if (!selected || state === 'uploading') return
    setFile(selected)
    setState(selected.size > MAX_FILE_SIZE ? 'sizeRejected' : 'selected')
    setMessage('')
  }

  const upload = async () => {
    if (!file || sizeRejected) return
    setState('uploading')
    setMessage('')
    try {
      await uploadApi.upload(file)
      setState('success')
      setMessage('업로드에 성공했습니다.')
    } catch (error) {
      const serverError = !error.status || error.status >= 500
      setState(serverError ? 'serverError' : 'blocked')
      setMessage(serverError ? SERVER_ERROR_MESSAGE : (error.message || SERVER_ERROR_MESSAGE))
    }
  }

  const reset = () => {
    setFile(null); setDragging(false); setState('idle'); setMessage('')
    if (fileInput.current) fileInput.current.value = ''
  }

  if (state === 'success' || state === 'blocked' || state === 'serverError') {
    const isSuccess = state === 'success'
    const isServerError = state === 'serverError'
    return (
      <section id="upload" className="panel result-panel" aria-live="polite">
        <div className={`status-icon ${isSuccess ? 'status-icon--success' : 'status-icon--error'}`}>{isSuccess ? '✓' : '!'}</div>
        <span className="eyebrow">{isSuccess ? 'UPLOAD COMPLETE' : isServerError ? 'UPLOAD ERROR' : 'UPLOAD REJECTED'}</span>
        <h2>{message}</h2>
        {file && <p className="result-file">{file.name} · {formatBytes(file.size)}</p>}
        <button className="button button--primary" onClick={isServerError ? upload : reset}>
          {isSuccess ? '새 파일 업로드' : isServerError ? '다시 시도' : '다른 파일 선택'}
        </button>
      </section>
    )
  }

  return (
    <section id="upload" className="panel" aria-labelledby="upload-title">
      <div className="panel-heading">
        <div><span className="eyebrow">UPLOAD</span><h2 id="upload-title">안전한 파일 업로드</h2><p>파일은 정책 확인과 보안 검사를 거친 후 저장됩니다.</p></div>
        <span className="limit-badge">최대 10 MB</span>
      </div>
      <div className={`drop-zone${dragging ? ' drop-zone--dragging' : ''}${file ? ' drop-zone--selected' : ''}`}
        aria-busy={state === 'uploading'}
        onDragEnter={(event) => { event.preventDefault(); if (state !== 'uploading') setDragging(true) }}
        onDragOver={(event) => event.preventDefault()}
        onDragLeave={() => setDragging(false)}
        onDrop={(event) => { event.preventDefault(); setDragging(false); selectFile(event.dataTransfer.files[0]) }}>
        <input ref={fileInput} type="file" hidden disabled={state === 'uploading'} onChange={(event) => selectFile(event.target.files[0])} />
        <div className="upload-mark">↑</div>
        {file ? <><strong>{file.name}</strong><span>{formatBytes(file.size)}</span><button className="text-button" type="button" disabled={state === 'uploading'} onClick={() => fileInput.current?.click()}>파일 변경</button></>
          : <><strong>파일을 여기로 끌어다 놓으세요</strong><span>또는</span><button className="button button--secondary" type="button" onClick={() => fileInput.current?.click()}>파일 선택</button></>}
      </div>
      {sizeRejected && <div className="notice notice--error" role="alert"><b>!</b> 파일 크기 제한을 초과했습니다.</div>}
      {!sizeRejected && extensionWarning && <div className="notice notice--warning"><b>!</b> 현재 정책상 차단된 확장자일 수 있습니다. 최종 판단은 서버에서 수행합니다.</div>}
      <button className="button button--primary button--full" disabled={!file || sizeRejected || state === 'uploading'} onClick={upload}>
        {state === 'uploading' ? <><Spinner small /> 업로드 중...</> : '보안 검사 후 업로드'}
      </button>
    </section>
  )
}

function App() {
  const [blockedExtensions, setBlockedExtensions] = useState(new Set())
  const [historyRevision, setHistoryRevision] = useState(0)
  const updatePolicy = useCallback((fixed, custom) => setBlockedExtensions(new Set([
    ...fixed.filter((item) => item.blocked).map((item) => item.extension),
    ...custom.map((item) => item.extension),
  ])), [])

  return (
    <div className="app-shell">
      <header className="topbar">
        <a className="brand" href="#top"><span className="brand-mark">F</span><span>FileGuard</span></a>
        <nav aria-label="주요 메뉴">
          <a href="#policy">차단 정책</a>
          <a href="#history">변경 이력</a>
          <a href="#upload">파일 업로드</a>
        </nav>
        <span className="environment">LOCAL</span>
      </header>
      <main id="top">
        <div className="page-intro">
          <p className="breadcrumb">SECURITY / FILE CONTROL</p>
          <h1>파일 업로드 보안</h1>
          <p>차단 정책을 관리하고, 선택한 파일을 다단계 보안 검사 후 안전하게 저장하세요.</p>
        </div>
        <div className="page-stack">
          <PolicyPanel
            onPolicyChange={updatePolicy}
            onHistoryChange={() => setHistoryRevision((value) => value + 1)}
          />
          <PolicyHistoryPanel key={historyRevision} />
          <UploadPanel blockedExtensions={blockedExtensions} />
        </div>
      </main>
      <footer><span>FileGuard</span><span>Server-side validation enabled</span></footer>
    </div>
  )
}

export default App
