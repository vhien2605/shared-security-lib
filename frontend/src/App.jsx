import { Routes, Route } from 'react-router-dom'
import routes from './routes'

function App() {
  return (
    <Routes>
      {routes.map((route, i) => (
        <Route key={i} path={route.path} element={route.element} />
      ))}
    </Routes>
  )
}

export default App
