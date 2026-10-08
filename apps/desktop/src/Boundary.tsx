import React from "react";
export class Boundary extends React.Component<
  { children: React.ReactNode },
  { failed: boolean }
> {
  state = { failed: false };
  static getDerivedStateFromError() {
    return { failed: true };
  }
  render() {
    return this.state.failed ? (
      <div className="empty">
        <h2>Não conseguimos abrir este módulo.</h2>
        <p>
          Seus registros e a fila local foram preservados. Reabra o módulo ou
          consulte o diagnóstico.
        </p>
        <button
          className="button primary"
          onClick={() => this.setState({ failed: false })}
        >
          Tentar novamente
        </button>
      </div>
    ) : (
      this.props.children
    );
  }
}
