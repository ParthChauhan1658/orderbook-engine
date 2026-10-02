const BASE = '/api'

export const getToken = () => localStorage.getItem('token')
export const setToken = (t) =>
    t ? localStorage.setItem('token', t) : localStorage.removeItem('token')

export async function api(path, { method = 'GET', body } = {}) {
    const headers = {}
    if (body) headers['Content-Type'] = 'application/json'
    const token = getToken()
    if (token) headers.Authorization = `Bearer ${token}`

    const res = await fetch(BASE + path, {
        method,
        headers,
        body: body ? JSON.stringify(body) : undefined,
    })

    if (res.status === 401 && !path.startsWith('/auth')) {
        setToken(null)
        window.dispatchEvent(new Event('logout'))
        throw new Error('Session expired, login again')
    }
    if (!res.ok) {
        let msg = res.statusText
        try {
            const j = await res.json()
            msg = j.error || j.message || msg
        } catch { /* body JSON nahi hai */ }
        throw new Error(`${res.status} ${msg}`)
    }
    const text = await res.text()
    return text ? JSON.parse(text) : null
}
export function usernameFromToken(token) {
    try {
        const payload = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')
        return JSON.parse(atob(payload)).sub
    } catch {
        return ''
    }
}