import { useState } from 'react'
import { api, usernameFromToken } from './api'
import { usePolling } from './usePolling'
import OrderForm from './OrderForm'
import Book from './Book'
import Trades from './Trades'
import MyOrders from './MyOrders'

export function Logo() {
    return (
        <svg className="logo" viewBox="0 0 28 28" aria-hidden="true">
            <rect width="28" height="28" rx="8" className="logo-bg" />
            <rect x="6" y="7" width="10" height="3" rx="1.5" fill="#f87171" />
            <rect x="6" y="12.5" width="16" height="3" rx="1.5" fill="#f87171" opacity=".55" />
            <rect x="6" y="18" width="13" height="3" rx="1.5" fill="#4ade80" />
        </svg>
    )
}

function Stat({ label, value, tone }) {
    const text = typeof value === 'number' ? value.toLocaleString() : value
    return (
        <div className="stat">
            <span className="stat-label">{label}</span>
            <span className={'stat-value num ' + (tone || '')}>{text ?? '—'}</span>
        </div>
    )
}

export default function Dashboard({ token, onLogout }) {
    const [refreshKey, setRefreshKey] = useState(0)
    const [pick, setPick] = useState(null)
    const refresh = () => setRefreshKey((k) => k + 1)

    const book = usePolling(() => api('/book'), 1000, refreshKey)
    const trades = usePolling(() => api('/trades?limit=20'), 1000, refreshKey)
    const online = !book.error && !trades.error

    const username = usernameFromToken(token)
    const bestBid = book.data?.bids[0]?.price
    const bestAsk = book.data?.asks[0]?.price
    const spread = bestBid && bestAsk ? bestAsk - bestBid : null
    const last = trades.data?.[0]?.price
    const prev = trades.data?.[1]?.price
    const lastTone = last != null && prev != null ? (last > prev ? 'up' : last < prev ? 'down' : '') : ''

    return (
        <div className="app">
            <header className="topbar">
                <div className="brand">
                    <Logo />
                    <span>Order Book</span>
                </div>
                <div className="topbar-right">
          <span className={'pill ' + (online ? 'live' : 'offline')}>
            <i /> {online ? 'Live' : 'Offline'}
          </span>
                    <div className="avatar" title={username}>{(username || '?')[0].toUpperCase()}</div>
                    <span className="username">{username}</span>
                    <button className="btn ghost" onClick={onLogout}>Log out</button>
                </div>
            </header>

            <section className="stats">
                <Stat label="Last price" value={last} tone={lastTone} />
                <Stat label="Best bid" value={bestBid} tone="up" />
                <Stat label="Best ask" value={bestAsk} tone="down" />
                <Stat label="Spread" value={spread} />
            </section>

            <main className="grid">
                <OrderForm onChange={refresh} pick={pick} />
                <Book
                    data={book.data}
                    error={book.error}
                    onPick={(price) => setPick({ price, n: Date.now() })}
                />
                <Trades data={trades.data} error={trades.error} />
                <MyOrders refreshKey={refreshKey} onChange={refresh} />
            </main>
        </div>
    )
}