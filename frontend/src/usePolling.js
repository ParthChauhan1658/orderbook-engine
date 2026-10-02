import { useEffect, useState } from 'react'

// fn ko har `ms` milliseconds pe call karta hai. dep badle to turant dobara fetch.
export function usePolling(fn, ms, dep) {
    const [data, setData] = useState(null)
    const [error, setError] = useState(null)

    useEffect(() => {
        let alive = true
        const tick = async () => {
            try {
                const d = await fn()
                if (alive) { setData(d); setError(null) }
            } catch (e) {
                if (alive) setError(e.message)
            }
        }
        tick()
        const id = setInterval(tick, ms)
        return () => { alive = false; clearInterval(id) }
    }, [dep])

    return { data, error }
}