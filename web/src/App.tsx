import { useEffect } from 'react'
import { Navigate, Route, Routes, useLocation } from 'react-router-dom'
import { Toaster } from 'sonner'
import { Shell } from './components/Shell'
import { useProfile } from './lib/profile'
import { ActivityPage } from './pages/Activity'
import { AddExpensePage } from './pages/AddExpense'
import { CreateGroupPage } from './pages/CreateGroup'
import { ExpenseDetailPage } from './pages/ExpenseDetail'
import { GroupDetailPage } from './pages/GroupDetail'
import { GroupsPage } from './pages/Groups'
import { HomePage } from './pages/Home'
import { JoinPage } from './pages/Join'
import { ProfilePage } from './pages/Profile'
import { WelcomePage } from './pages/Welcome'

function useThemeClass() {
  const theme = useProfile((s) => s.theme)
  useEffect(() => {
    const mq = window.matchMedia('(prefers-color-scheme: dark)')
    const apply = () => {
      const dark = theme === 'dark' || (theme === 'system' && mq.matches)
      document.documentElement.classList.toggle('dark', dark)
      document.querySelector('meta[name="theme-color"]')?.setAttribute('content', dark ? '#0C130E' : '#163D2A')
    }
    apply()
    mq.addEventListener('change', apply)
    return () => mq.removeEventListener('change', apply)
  }, [theme])
}

export default function App() {
  useThemeClass()
  const onboarded = useProfile((s) => s.onboarded)
  const loc = useLocation()
  const dark = useProfile((s) => s.theme) === 'dark'

  if (!onboarded && loc.pathname !== '/welcome') {
    const next = encodeURIComponent(loc.pathname + loc.search)
    return <Navigate to={`/welcome?next=${next}`} replace />
  }

  return (
    <>
      <Routes>
        <Route path="/welcome" element={<WelcomePage />} />
        <Route element={<Shell />}>
          <Route index element={<HomePage />} />
          <Route path="/groups" element={<GroupsPage />} />
          <Route path="/activity" element={<ActivityPage />} />
          <Route path="/profile" element={<ProfilePage />} />
          <Route path="/new/:type" element={<CreateGroupPage />} />
          <Route path="/g/:id" element={<GroupDetailPage />} />
          <Route path="/add" element={<AddExpensePage />} />
          <Route path="/e/:id" element={<ExpenseDetailPage />} />
          <Route path="/e/:id/edit" element={<AddExpensePage />} />
          <Route path="/join" element={<JoinPage />} />
        </Route>
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
      <Toaster position="top-center" richColors theme={dark ? 'dark' : 'light'} toastOptions={{ style: { fontFamily: 'Manrope', borderRadius: 16 } }} />
    </>
  )
}
