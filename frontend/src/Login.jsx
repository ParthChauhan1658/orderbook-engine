import { useState } from 'react'
import { api, setToken } from './api'
import { Logo } from './Dashboard'

export default function Login({ onLogin }) {
    const [username, setUsername] = useState('')
    const [password, setPassword] = useState('')
    const [error, setError] = useState('')
    const [busy, setBusy] = useState(false)

    async function go(kind) {
        setError('')
        setBusy(true)
        try {
            const r = await api(`/auth/${kind}`, { method: 'POST', body: { username, password } })
            setToken(r.token)
            onLogin(r.token)
        } catch (e) {
            setError(e.message)
        } finally {
            setBusy(false)
        }
    }

    return (
        <div className="login-wrap">
            <form className="login-card" onSubmit={(e) => { e.preventDefault(); go('login') }}>
                <div className="login-brand">
                    <Logo />
                    <h1>Order Book</h1>
                    <p>Sign in to start trading</p>
                </div>
                <label>Username
                    <input value={username} onChange={(e) => setUsername(e.target.value)} autoFocus required />
                </label>
                <label>Password
                    <input type="password" value={password} minLength={6}
                           onChange={(e) => setPassword(e.target.value)} required />
                </label>
                {error && <p className="msg err">{error}</p>}
                <button type="submit" className="btn primary" disabled={busy}>Sign in</button>
                <button type="button" className="btn ghost" disabled={busy} onClick={() => go('register')}>
                    Create account
                </button>
            </form>
        </div>
    )
}