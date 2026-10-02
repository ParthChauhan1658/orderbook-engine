import { useEffect, useState } from 'react'
import { getToken, setToken } from './api'
import Login from './Login'
import Dashboard from './Dashboard'

export default function App() {
  const [token, setTokenState] = useState(getToken())

  useEffect(() => {
    const onLogout = () => setTokenState(null)
    window.addEventListener('logout', onLogout)
    return () => window.removeEventListener('logout', onLogout)
  }, [])

  if (!token) return <Login onLogin={setTokenState} />

  return (
      <Dashboard
          token={token}
          onLogout={() => { setToken(null); setTokenState(null) }}
      />
  )
}