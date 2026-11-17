import { MapPinLine } from '@phosphor-icons/react';
import { EmptyState, LinkButton } from '../components/ui';

export function NotFound() {
  return (
    <main className="min-h-screen flex items-center justify-center p-4">
      <div className="w-full max-w-xl">
        <EmptyState
          icon={<MapPinLine size={24} aria-hidden="true" />}
          title="This page does not exist"
          action={<LinkButton to="/rooms/find">Find a room</LinkButton>}
        >
          The link may be old or mistyped. Head back to the room search to continue.
        </EmptyState>
      </div>
    </main>
  );
}
