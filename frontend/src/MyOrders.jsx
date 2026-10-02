import { useState } from 'react'
import { api } from './api'
import { usePolling } from './usePolling'

const isOpen = (o) => o.status === 'NEW' || o.status === 'PARTIALLY_FILLED'
const label = (s) => s.charAt(0) + s.slice(1).toLowerCase().replace('_', ' ')

export default function MyOrders({ refreshKey, onChange }) {
    const [filter, setFilter] = useState('open')
    const { data, error } = usePolling(() => api('/orders/mine?size=50'), 2000, refreshKey)
    const rows = (data || []).filter((o) => filter === 'all' || isOpen(o))

    async function cancel(id) {
        try {
            await api(`/orders/${id}`, { method: 'DELETE' })
        } catch {
            /* the next poll shows the real state */
        }
        onChange()
    }

    return (
        <div className="card wide">
            <div className="card-head">
                <h3>My orders</h3>
                <div className="segmented sm tabs">
                    <button className={filter === 'open' ? 'on' : ''} onClick={() => setFilter('open')}>Open</button>
                    <button className={filter === 'all' ? 'on' : ''} onClick={() => setFilter('all')}>All</button>
                </div>
            </div>
            {error && <p className="msg err">{error}</p>}
            {data && rows.length === 0 ? (
                <div className="empty">{filter === 'open' ? 'No open orders.' : 'No orders yet.'}</div>
            ) : (
                <div className="scroll">
                    <table>
                        <thead>
                        <tr>
                            <th>ID</th><th>Side</th><th>Type</th><th className="r">Price</th>
                            <th>Filled</th><th>Status</th><th></th>
                        </tr>
                        </thead>
                        <tbody>
                        {rows.map((o) => (
                            <tr key={o.id}>
                                <td className="num muted">#{o.id}</td>
                                <td className={o.side === 'BUY' ? 'up' : 'down'}>{o.side === 'BUY' ? 'Buy' : 'Sell'}</td>
                                <td>{o.type === 'LIMIT' ? 'Limit' : 'Market'}</td>
                                <td className="num r">{o.type === 'MARKET' ? 'Market' : o.price.toLocaleString()}</td>
                                <td>
                                    <div className="num">{o.filledQuantity}/{o.quantity}</div>
                                    <div className="bar"><div style={{ width: (o.filledQuantity / o.quantity) * 100 + '%' }} /></div>
                                </td>
                                <td><span className={'badge s-' + o.status}>{label(o.status)}</span></td>
                                <td className="r">
                                    {isOpen(o) && <button className="btn mini" onClick={() => cancel(o.id)}>Cancel</button>}
                                </td>
                            </tr>
                        ))}
                        </tbody>
                    </table>
                </div>
            )}
        </div>
    )
}