import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { StaffWorkspace } from "./auth";
import "./styles.css";

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <StaffWorkspace />
  </StrictMode>,
);
