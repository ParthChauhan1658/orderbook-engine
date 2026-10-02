import { useEffect, useState } from 'react'
import { api } from './api'

export default function OrderForm({ onChange, pick }) {
    const [side, setSide] = useState('BUY')
    const [type, setType] = useState('LIMIT')
    const [price, setPrice] = useState('')
    const [quantity, setQuantity] = useState('')
    const [msg, setMsg] = useState('')
    const [isError, setIsError] = useState(false)
    const [busy, setBusy] = useState(false)

    useEffect(() => {
        if (pick) {
            setType('LIMIT')
            setPrice(String(pick.price))
        }
    }, [pick])

    const total = type === 'LIMIT' && price && quantity ? Number(price) * Number(quantity) : null

    async function submit(e) {
        e.preventDefault()
        setBusy(true)
        try {
            const body = {
                side,
                type,
                price: type === 'MARKET' ? 0 : Number(price),
                quantity: Number(quantity),
            }
            const r = await api('/orders', { method: 'POST', body })
            setMsg(`Order #${r.orderId} accepted`)
            setIsError(false)
            setQuantity('')
            onChange()
        } catch (err) {
            setMsg(err.message)
            setIsError(true)
        } finally {
            setBusy(false)
        }
    }

    return (
        <form className="card" onSubmit={submit}>
            <div className="card-head"><h3>New order</h3></div>

            <div className="segmented">
                <button type="button" className={side === 'BUY' ? 'on buy' : ''} onClick={() => setSide('BUY')}>Buy</button>
                <button type="button" className={side === 'SELL' ? 'on sell' : ''} onClick={() => setSide('SELL')}>Sell</button>
            </div>

            <div className="segmented sm">
                <button type="button" className={type === 'LIMIT' ? 'on' : ''} onClick={() => setType('LIMIT')}>Limit</button>
                <button type="button" className={type === 'MARKET' ? 'on' : ''} onClick={() => setType('MARKET')}>Market</button>
            </div>

            {type === 'LIMIT' && (
                <label>Price
                    <input type="number" min="1" value={price} onChange={(e) => setPrice(e.target.value)} required />
                </label>
            )}
            <label>Quantity
                <input type="number" min="1" value={quantity} onChange={(e) => setQuantity(e.target.value)} required />
            </label>

            <div className="total">
                <span>Estimated total</span>
                <span className="num">{total !== null ? total.toLocaleString() : '—'}</span>
            </div>

            <button type="submit" disabled={busy} className={'btn submit ' + (side === 'BUY' ? 'buy' : 'sell')}>
                {busy ? 'Sending…' : `${side === 'BUY' ? 'Buy' : 'Sell'} ${type === 'LIMIT' ? 'limit' : 'market'}`}
            </button>
            {msg && <p className={'msg ' + (isError ? 'err' : 'ok')}>{msg}</p>}
        </form>
    )
}