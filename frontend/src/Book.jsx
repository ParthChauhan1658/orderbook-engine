function Row({ level, side, max, onPick }) {
    const w = max ? Math.round((level.quantity / max) * 100) : 0
    return (
        <tr className={side} style={{ '--w': w + '%' }} onClick={() => onPick(level.price)} title="Use this price">
            <td className="price num">{level.price.toLocaleString()}</td>
            <td className="num r">{level.quantity.toLocaleString()}</td>
        </tr>
    )
}

export default function Book({ data, error, onPick }) {
    const asks = data ? [...data.asks].reverse() : []
    const bids = data ? data.bids : []
    const max = Math.max(0, ...asks.map((l) => l.quantity), ...bids.map((l) => l.quantity))
    const empty = data && asks.length === 0 && bids.length === 0
    const spread = data?.asks[0] && data?.bids[0] ? data.asks[0].price - data.bids[0].price : null

    return (
        <div className="card">
            <div className="card-head">
                <h3>Order book</h3>
                <span className="chip">Top 5</span>
            </div>
            {error && <p className="msg err">{error}</p>}
            {empty ? (
                <div className="empty">The book is empty.<br />Place an order to get started.</div>
            ) : (
                <table className="book">
                    <thead>
                    <tr><th>Price</th><th className="r">Quantity</th></tr>
                    </thead>
                    <tbody>
                    {asks.map((l) => <Row key={'a' + l.price} level={l} side="ask" max={max} onPick={onPick} />)}
                    <tr className="spread">
                        <td colSpan="2">{spread !== null ? `Spread ${spread}` : '—'}</td>
                    </tr>
                    {bids.map((l) => <Row key={'b' + l.price} level={l} side="bid" max={max} onPick={onPick} />)}
                    </tbody>
                </table>
            )}
        </div>
    )
}