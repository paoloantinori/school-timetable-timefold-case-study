import React, { useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { useToast, ToastType } from '../../contexts/ToastContext';

const typeStyles: Record<ToastType, { bg: string; icon: string; border: string }> = {
  success: {
    bg: 'bg-green-500/95 dark:bg-green-600/95',
    icon: '✓',
    border: 'border-green-400',
  },
  error: {
    bg: 'bg-red-500/95 dark:bg-red-600/95',
    icon: '✕',
    border: 'border-red-400',
  },
  info: {
    bg: 'bg-blue-500/95 dark:bg-blue-600/95',
    icon: 'ℹ',
    border: 'border-blue-400',
  },
  warning: {
    bg: 'bg-yellow-500/95 dark:bg-yellow-600/95',
    icon: '⚠',
    border: 'border-yellow-400',
  },
};

function ToastItem({
  id,
  message,
  type,
  duration,
}: {
  id: string;
  message: string;
  type: ToastType;
  duration?: number;
}) {
  const { removeToast } = useToast();
  const { t } = useTranslation('common');
  const style = typeStyles[type];

  useEffect(() => {
    if (duration && duration > 0) {
      const timer = setTimeout(() => {
        removeToast(id);
      }, duration);
      return () => clearTimeout(timer);
    }
  }, [id, duration, removeToast]);

  return (
    <div
      className={`
        flex items-start gap-3 px-4 py-3 rounded-lg shadow-lg
        ${style.bg} text-white
        border-l-4 ${style.border}
        animate-[slideInRight_0.3s_ease-out]
        min-w-[280px] max-w-[400px]
        backdrop-blur-sm
      `}
      role="alert"
    >
      <span className="text-lg flex-shrink-0 opacity-90">{style.icon}</span>
      <p className="flex-1 text-sm font-medium leading-snug">{message}</p>
      <button
        onClick={() => removeToast(id)}
        className="flex-shrink-0 text-white/70 hover:text-white transition-colors cursor-pointer bg-transparent border-none text-lg leading-none p-0"
        aria-label={t('common:close')}
      >
        ×
      </button>
    </div>
  );
}

export default function ToastContainer() {
  const { toasts } = useToast();

  if (toasts.length === 0) return null;

  return (
    <div
      className="fixed bottom-4 right-4 z-[9999] flex flex-col gap-2"
      aria-live="polite"
      aria-label="Notifications"
    >
      {toasts.map(toast => (
        <ToastItem key={toast.id} {...toast} />
      ))}
    </div>
  );
}
