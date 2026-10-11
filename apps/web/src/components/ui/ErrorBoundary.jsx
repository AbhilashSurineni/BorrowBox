import { Component } from 'react';
import { Button } from './Button';

export class ErrorBoundary extends Component {
  state = { error: null };

  static getDerivedStateFromError(error) {
    return { error };
  }

  componentDidCatch(error, info) {
    console.error('[ui] uncaught error', error, info.componentStack);
  }

  componentDidUpdate(prev) {
    if (prev.resetKey !== this.props.resetKey && this.state.error) this.setState({ error: null });
  }

  render() {
    if (!this.state.error) return this.props.children;
    return (
      <div role="alert" className="mx-auto max-w-lg px-4 py-16 text-center">
        <div className="text-5xl" aria-hidden>
          🧰
        </div>
        <h1 className="mt-4 text-xl font-semibold text-gray-900">Something went wrong</h1>
        <p className="mt-2 text-sm text-gray-600">This page hit an unexpected error. You can try again or go back home.</p>
        <div className="mt-6 flex justify-center gap-3">
          <Button onClick={() => this.setState({ error: null })}>Try again</Button>
          <Button variant="secondary" onClick={() => (window.location.href = '/')}>
            Go home
          </Button>
        </div>
      </div>
    );
  }
}
