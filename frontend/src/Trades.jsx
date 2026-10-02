export default function Trades({ data, error }) {
    const trades = data || []

    return (
        <div className="card">
            <div className="card-head">
                <h3>Recent trades</h3>
                <span className="chip">{trades.length}</span>
            </div>
            {error && <p className="msg err">{error}</p>}
            {data && trades.length === 0 ? (
                <div className="empty">No trades yet.</div>
            ) : (
                <table>
                    <thead>
                    <tr><th>Price</th><th className="r">Qty</th><th className="r">Maker</th><th className="r">Taker</th></tr>
                    </thead>
                    <tbody>
                    {trades.map((t, i) => {
                        const older = trades[i + 1]
                        const tone = older ? (t.price > older.price ? 'up' : t.price < older.price ? 'down' : '') : ''
                        return (
                            <tr key={t.sequence}>
                                <td className={'num ' + tone}>{t.price.toLocaleString()}</td>
                                <td className="num r">{t.quantity.toLocaleString()}</td>
                                <td className="num r muted">#{t.makerOrderId}</td>
                                <td className="num r muted">#{t.takerOrderId}</td>
                            </tr>
                        )
                    })}
                    </tbody>
                </table>
            )}
        </div>
    )
}