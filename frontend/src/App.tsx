import { useUIStore } from "./stores/useUIStore";
import { useGameStore } from "./stores/useGameStore";
import { JoinView } from "./views/JoinView";
import { QuestionView } from "./views/QuestionView";
import { ResultView } from "./views/ResultView";
import { HostLobbyView } from "./views/HostLobbyView";
import { AdminDashboard } from "./views/AdminDashboard";

export function App() {
  const view = useUIStore((s) => s.view);
  const status = useGameStore((s) => s.status);

  if (view === "admin") return <AdminDashboard />;
  if (view === "host") return <HostLobbyView />;
  if (view === "play") return status === "ENDED" ? <ResultView /> : <QuestionView />;
  return <JoinView />;
}
