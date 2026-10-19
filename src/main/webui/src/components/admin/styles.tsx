// Tailwind-based table component library for admin pages
// These components replace the old CSSProperties-based tableStyles

import i18n from '../../i18n';

export function TableContainer({ children }: { children: React.ReactNode }) {
  return (
    <div className="bg-surface rounded-lg p-5 shadow-soft">
      {children}
    </div>
  );
}

export function TableTitle({ children }: { children: React.ReactNode }) {
  return (
    <h3 className="text-lg font-bold mb-4 text-content">{children}</h3>
  );
}

export function TableForm({ children }: { children: React.ReactNode }) {
  return (
    <form className="flex gap-2 mb-4 flex-wrap">{children}</form>
  );
}

export function TableInput(props: React.InputHTMLAttributes<HTMLInputElement>) {
  return (
    <input
      className="px-2 py-2 border border-border rounded text-sm min-w-[150px]
                 bg-surface text-content focus:outline-none focus:ring-2 focus:ring-accent"
      {...props}
    />
  );
}

export function TableSelect(props: React.SelectHTMLAttributes<HTMLSelectElement>) {
  return (
    <select
      className="px-2 py-2 border border-border rounded text-sm min-w-[150px]
                 bg-surface text-content focus:outline-none focus:ring-2 focus:ring-accent"
      {...props}
    />
  );
}

export function TableButton({
  variant = 'primary',
  children,
  ...props
}: {
  variant?: 'primary' | 'success' | 'cancel';
  children: React.ReactNode;
} & React.ButtonHTMLAttributes<HTMLButtonElement>) {
  const variantClasses = {
    primary: 'bg-blue-500 text-white hover:bg-blue-600',
    success: 'bg-green-500 text-white hover:bg-green-600',
    cancel: 'bg-cancel-bg text-cancel-text hover:opacity-80',
  };

  return (
    <button
      className={`px-4 py-2 rounded cursor-pointer text-sm font-semibold
                  transition-colors ${variantClasses[variant]}`}
      {...props}
    >
      {children}
    </button>
  );
}

export function AdminTable({ children, 'data-testid': testId }: { children: React.ReactNode; 'data-testid'?: string }) {
  return (
    <table data-testid={testId} className="w-full border-collapse text-sm">
      {children}
    </table>
  );
}

export function AdminTh({ children, className = '' }: { children: React.ReactNode; className?: string }) {
  return (
    <th className={`text-left p-2 border-b-2 border-border-light font-semibold
                   text-muted ${className}`}>
      {children}
    </th>
  );
}

export function AdminTd({ children, className = '' }: { children: React.ReactNode; className?: string }) {
  return (
    <td className={`p-2 border-b border-border-faint text-content ${className}`}>
      {children}
    </td>
  );
}

export function ActionButton({
  children,
  onClick,
  title,
  disabled,
}: {
  children: React.ReactNode;
  onClick?: () => void;
  title?: string;
  disabled?: boolean;
}) {
  return (
    <button
      className={`px-2 py-1 bg-action text-action-text border-none rounded
                   cursor-pointer text-xs mr-1 transition-opacity
                   ${disabled ? 'opacity-50 cursor-not-allowed' : 'hover:opacity-80'}`}
      onClick={onClick}
      title={title}
      disabled={disabled}
    >
      {children}
    </button>
  );
}

export function DeleteButton({
  children,
  onClick,
  title,
  disabled,
  skipConfirm = false,
  'data-testid': testId,
}: {
  children: React.ReactNode;
  onClick?: () => void;
  title?: string;
  disabled?: boolean;
  /** Callers that run their own contextual confirm (e.g. CourseGrid's per-subject
   *  message) set this to avoid a double dialog with the centralized one. */
  skipConfirm?: boolean;
  'data-testid'?: string;
}) {
  return (
    <button
      data-testid={testId}
      className={`px-2 py-1 bg-danger text-danger-text border-none rounded
                   cursor-pointer text-xs transition-opacity
                   ${disabled ? 'opacity-50 cursor-not-allowed' : 'hover:opacity-80'}`}
      onClick={() => {
        // UX TASK-513: admin deletes are irreversible CRUD calls; explicit confirm required
        // unless the caller supplies its own richer confirm (skipConfirm).
        if (skipConfirm || window.confirm(i18n.t('common:confirmDelete'))) onClick?.();
      }}
      title={title}
      disabled={disabled}
    >
      {children}
    </button>
  );
}


/**
 * The admin pages' dismissible feedback banner (error + success share the shape;
 * the class strings are the theme's feedback-* variables). Extracted from
 * GroupCalendarAdmin's private copy (TASK-603) so new pages import it instead of
 * growing the dozen inline duplicates.
 */
export function StatusBanner({ kind, text, onDismiss, dismissLabel }: {
  kind: 'error' | 'success';
  text: string;
  onDismiss: () => void;
  dismissLabel: string;
}) {
  return (
    <div className={`p-2 bg-feedback-${kind}-bg rounded mb-3 text-sm text-feedback-${kind}-text flex justify-between items-center`}>
      <span>{text}</span>
      <button
        className={`bg-none border-none text-feedback-${kind}-text cursor-pointer text-lg leading-none`}
        onClick={onDismiss}
        aria-label={dismissLabel}
      >
        ×
      </button>
    </div>
  );
}
