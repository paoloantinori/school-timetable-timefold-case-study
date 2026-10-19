import { createContext, useContext, useState, useEffect, type ReactNode } from 'react';

export type Mode = 'light' | 'dark' | 'school';

const themes: Record<Mode, Record<string, string>> = {
  light: {
    '--bg':             '#f5f5f5',
    '--surface':        '#fff',
    '--surface-alt':    '#f9fafb',
    '--surface-hover':  '#f3f4f6',
    '--text':           '#333',
    '--text-secondary': '#555',
    '--text-muted':     '#6b7280',
    '--text-faint':     '#9ca3af',
    '--border':         '#d1d5db',
    '--border-light':   '#e5e7eb',
    '--border-faint':   '#f0f0f0',
    '--input-border':   '#ddd',
    '--shadow':         'rgba(0,0,0,0.1)',
    '--overlay':        'rgba(0,0,0,0.3)',
    // Schedule-specific
    '--day-header-bg':  '#e5e7eb',
    '--header-bg':      '#f3f4f6',
    '--corner-bg':      '#f9fafb',
    // Vacation/Holiday days (red/pink - "no school" indicator)
    '--holiday-bg':           '#ffe4e6',        // Light red/pink background
    '--holiday-border':       '#fda4af',        // Pink border
    '--holiday-accent':       '#e11d48',        // Strong red accent
    // Stage days (purple/indigo - "stage period" indicator)
    '--stage-bg':             '#eef2ff',        // Light indigo background
    '--stage-border':         '#a5b4fc',        // Indigo border
    '--stage-highlight':      '#c7d2fe',        // Light indigo highlight
    '--stage-cell':           'rgba(99, 102, 241, 0.12)', // More visible indigo tint
    // Completion day (blue/cyan - "theoretical end" indicator)
    '--completion-bg':        '#ecfeff',        // Light cyan background
    '--completion-border':    '#67e8f9',        // Cyan border
    '--completion-accent':    '#0891b2',        // Strong cyan accent
    '--header-text':    '#333333',      // dark text for light gray headers
    // Admin-specific
    '--action-bg':      '#e8f0fe',
    '--action-text':    '#1a73e8',
    '--active-link-bg': '#e8f0fe',
    '--active-link':    '#1a73e8',
    '--delete-bg':      '#fee2e2',
    '--cancel-bg':      '#e5e7eb',
    '--cancel-text':    '#333',
    // Pre-assign row
    '--pre-assign-bg':  '#f8fafc',
    '--pre-assign-label': '#475569',
    // Elapsed time
    '--elapsed-text':   '#7c3aed',
    // Nav (fallback for light theme - uses dark nav)
    '--nav-bg':         '#1a1a2e',
    '--nav-text':       '#fff',
    '--nav-hover':      '#7c3aed',
    // Score card colors
    '--score-hard-bg':     '#fef2f2',
    '--score-medium-bg':   '#fffbeb',
    '--score-soft-bg':     '#ecfdf5',
    '--score-hard-border': '#ef4444',
    '--score-medium-border': '#f59e0b',
    '--score-soft-border': '#10b981',
    // Toast notifications
    '--toast-success-bg':     'rgba(204, 251, 241, 0.8)',
    '--toast-success-border': '#99f6e4',
    '--toast-success-text':   '#0d9488',
    '--toast-success-hover':  '#ccfbf1',
    '--toast-error-bg':       'rgba(254, 226, 226, 0.8)',
    '--toast-error-border':   '#fecaca',
    '--toast-error-text':     '#dc2626',
    // Feedback banners (admin error/success/warning)
    '--feedback-error-bg':      '#fef2f2',
    '--feedback-error-border':  '#fecaca',
    '--feedback-error-text':    '#dc2626',
    '--feedback-success-bg':    '#f0fdf4',
    '--feedback-success-border':'#bbf7d0',
    '--feedback-success-text':  '#16a34a',
    '--feedback-warning-bg':    '#fffbeb',
    '--feedback-warning-border':'#fde68a',
    '--feedback-warning-text':  '#d97706',
    // Danger button (sidebar reset)
    '--danger-bg':        '#fef2f2',
    '--danger-border':    '#fecaca',
    '--danger-text':      '#dc2626',
    '--danger-hover-bg':  '#fee2e2',
    // Constraint level accent colors (dots/badges)
    '--constraint-hard':   '#ef4444',
    '--constraint-medium': '#f59e0b',
    '--constraint-soft':   '#3b82f6',
    // Phase indicator badges
    '--phase-ch-bg':            'rgba(245,158,11,0.15)',
    '--phase-ch-text':          '#d97706',
    '--phase-ls-compact-bg':    'rgba(59,130,246,0.15)',
    '--phase-ls-compact-text':  '#2563eb',
    '--phase-ls-conflict-bg':   'rgba(34,197,94,0.15)',
    '--phase-ls-conflict-text': '#16a34a',
    '--phase-ls-room-bg':       'rgba(168,85,247,0.15)',
    '--phase-ls-room-text':     '#9333ea',
    '--phase-pre-assign-bg':    'rgba(20,184,166,0.15)',
    '--phase-pre-assign-text':  '#0d9488',
  },
  dark: {
    // True black OLED-friendly theme (#000000 pixels consume no power on OLED displays)
    '--bg':             '#000000',
    '--surface':        '#0a0a0a',
    '--surface-alt':    '#121212',
    '--surface-hover':  '#1a1a1a',
    // Text colors tuned for WCAG AA contrast against black (4.5:1 minimum)
    '--text':           '#f5f5f5',   // 17.5:1 contrast
    '--text-secondary': '#e0e0e0',   // 14:1 contrast
    '--text-muted':     '#a0a0a0',   // 5.7:1 contrast
    '--text-faint':     '#707070',   // 3.9:1 contrast (AA Large only)
    '--border':         '#2a2a2a',
    '--border-light':   '#1a1a1a',
    '--border-faint':   '#0f0f0f',
    '--input-border':   '#3a3a3a',
    '--shadow':         'rgba(0,0,0,0.6)',
    '--overlay':        'rgba(0,0,0,0.75)',
    // Schedule-specific
    '--day-header-bg':  '#1a1a1a',
    '--header-bg':      '#0a0a0a',
    '--corner-bg':      '#0f0f0f',
    // Vacation/Holiday days (red/pink - dark mode)
    '--holiday-bg':           '#4c0519',        // Dark red background
    '--holiday-border':       '#be123c',        // Pink/red border
    '--holiday-accent':       '#fb7185',        // Light pink accent
    // Stage days (purple/indigo - dark mode)
    '--stage-bg':             '#312e81',        // Dark indigo background
    '--stage-border':         '#818cf8',        // Light indigo border
    '--stage-highlight':      '#6366f1',        // Indigo highlight
    '--stage-cell':           'rgba(129, 140, 248, 0.18)', // More visible indigo tint
    // Completion day (blue/cyan - dark mode)
    '--completion-bg':        '#164e63',        // Dark cyan background
    '--completion-border':    '#22d3ee',        // Cyan border
    '--completion-accent':    '#67e8f9',        // Light cyan accent
    '--header-text':    '#f5f5f5',      // light text for dark headers
    // Admin-specific
    '--action-bg':      '#0d1f3c',
    '--action-text':    '#60a5fa',
    '--active-link-bg': '#0d1f3c',
    '--active-link':    '#60a5fa',
    '--delete-bg':      '#2a0a0a',
    '--cancel-bg':      '#1a1a1a',
    '--cancel-text':    '#f5f5f5',
    // Pre-assign row
    '--pre-assign-bg':  '#0a0a0a',
    '--pre-assign-label': '#a0a0a0',
    // Elapsed time (bright purple for true black contrast)
    '--elapsed-text':   '#c084fc',
    // Nav (fallback for dark theme - uses dark nav)
    '--nav-bg':         '#1a1a2e',
    '--nav-text':       '#fff',
    '--nav-hover':      '#7c3aed',
    // Score card colors (solid dark backgrounds for true black contrast)
    '--score-hard-bg':     '#450a0a',
    '--score-medium-bg':   '#451a03',
    '--score-soft-bg':     '#022c22',
    '--score-hard-border': '#f87171',
    '--score-medium-border': '#fbbf24',
    '--score-soft-border': '#34d399',
    // Toast notifications (dark-aware)
    '--toast-success-bg':     'rgba(13, 148, 136, 0.15)',
    '--toast-success-border': '#115e59',
    '--toast-success-text':   '#5eead4',
    '--toast-success-hover':  'rgba(13, 148, 136, 0.25)',
    '--toast-error-bg':       'rgba(239, 68, 68, 0.15)',
    '--toast-error-border':   '#7f1d1d',
    '--toast-error-text':     '#fca5a5',
    // Feedback banners
    '--feedback-error-bg':      'rgba(239,68,68,0.15)',
    '--feedback-error-border':  '#7f1d1d',
    '--feedback-error-text':    '#fca5a5',
    '--feedback-success-bg':    'rgba(34,197,94,0.15)',
    '--feedback-success-border':'#14532d',
    '--feedback-success-text':  '#86efac',
    '--feedback-warning-bg':    'rgba(245,158,11,0.15)',
    '--feedback-warning-border':'#78350f',
    '--feedback-warning-text':  '#fbbf24',
    // Danger button
    '--danger-bg':        'rgba(239,68,68,0.12)',
    '--danger-border':    '#7f1d1d',
    '--danger-text':      '#fca5a5',
    '--danger-hover-bg':  'rgba(239,68,68,0.2)',
    // Constraint level accent colors
    '--constraint-hard':   '#f87171',
    '--constraint-medium': '#fbbf24',
    '--constraint-soft':   '#60a5fa',
    // Phase indicator badges
    '--phase-ch-bg':            'rgba(245,158,11,0.15)',
    '--phase-ch-text':          '#fbbf24',
    '--phase-ls-compact-bg':    'rgba(59,130,246,0.15)',
    '--phase-ls-compact-text':  '#60a5fa',
    '--phase-ls-conflict-bg':   'rgba(34,197,94,0.15)',
    '--phase-ls-conflict-text': '#4ade80',
    '--phase-ls-room-bg':       'rgba(168,85,247,0.15)',
    '--phase-ls-room-text':     '#c084fc',
    '--phase-pre-assign-bg':    'rgba(20,184,166,0.15)',
    '--phase-pre-assign-text':  '#5eead4',
  },
  school: {
    // the school brand colors (from example.it)
    // Primary: #e84e0f (brand orange), Accent: #003366 (navy)
    '--bg':             '#ffffff',
    '--surface':        '#f8f9fa',
    '--surface-alt':    '#f1f3f5',
    '--surface-hover':  '#e9ecef',
    '--text':           '#222222',
    '--text-secondary': '#495057',
    '--text-muted':     '#6c757d',
    '--text-faint':     '#adb5bd',
    '--border':         '#dee2e6',
    '--border-light':   '#e9ecef',
    '--border-faint':   '#f1f3f5',
    '--input-border':   '#ced4da',
    '--shadow':         'rgba(0,0,0,0.1)',
    '--overlay':        'rgba(0,51,102,0.5)',
    // Schedule-specific - navy headers need white text
    '--day-header-bg':  '#003366',
    '--header-bg':      '#003366',
    '--corner-bg':      '#002244',
    '--header-text':    '#ffffff',      // white text for navy headers
    // Vacation/Holiday days (red/pink - school brand compatible)
    '--holiday-bg':           '#ffe4e6',        // Light red/pink background
    '--holiday-border':       '#fda4af',        // Pink border
    '--holiday-accent':       '#e11d48',        // Strong red accent
    // Stage days (purple/indigo - school brand compatible)
    '--stage-bg':             '#eef2ff',        // Light indigo background
    '--stage-border':         '#a5b4fc',        // Indigo border
    '--stage-highlight':      '#c7d2fe',        // Light indigo highlight
    '--stage-cell':           'rgba(99, 102, 241, 0.12)', // Indigo tint
    // Completion day (blue/cyan - school brand compatible)
    '--completion-bg':        '#ecfeff',        // Light cyan background
    '--completion-border':    '#67e8f9',        // Cyan border
    '--completion-accent':    '#0891b2',        // Strong cyan accent
    // Admin-specific
    '--action-bg':      '#e84e0f',
    '--action-text':    '#ffffff',
    '--active-link-bg': '#e84e0f',
    '--active-link':    '#ffffff',
    '--delete-bg':      '#fff3f0',
    '--cancel-bg':      '#e9ecef',
    '--cancel-text':    '#495057',
    // Pre-assign row
    '--pre-assign-bg':  '#fff8f5',
    '--pre-assign-label': '#c2410c',
    // Elapsed time
    '--elapsed-text':   '#e84e0f',
    // school-specific nav
    '--nav-bg':         '#e84e0f',
    '--nav-text':       '#ffffff',
    '--nav-hover':      '#c4410c',
    // Score card colors (brand-aligned)
    '--score-hard-bg':     '#fef2f2',
    '--score-medium-bg':   '#fff7ed',
    '--score-soft-bg':     '#ecfdf5',
    '--score-hard-border': '#ef4444',
    '--score-medium-border': '#ea580c',
    '--score-soft-border': '#10b981',
    // Toast notifications (brand-aligned)
    '--toast-success-bg':     'rgba(204, 251, 241, 0.8)',
    '--toast-success-border': '#99f6e4',
    '--toast-success-text':   '#0d9488',
    '--toast-success-hover':  '#ccfbf1',
    '--toast-error-bg':       'rgba(254, 226, 226, 0.8)',
    '--toast-error-border':   '#fecaca',
    '--toast-error-text':     '#dc2626',
    // Feedback banners (brand-aligned)
    '--feedback-error-bg':      '#fef2f2',
    '--feedback-error-border':  '#fecaca',
    '--feedback-error-text':    '#dc2626',
    '--feedback-success-bg':    '#f0fdf4',
    '--feedback-success-border':'#bbf7d0',
    '--feedback-success-text':  '#16a34a',
    '--feedback-warning-bg':    '#fff7ed',
    '--feedback-warning-border':'#fed7aa',
    '--feedback-warning-text':  '#c2410c',
    // Danger button
    '--danger-bg':        '#fff3f0',
    '--danger-border':    '#fecaca',
    '--danger-text':      '#dc2626',
    '--danger-hover-bg':  '#fee2e2',
    // Constraint level accent colors (brand-aligned)
    '--constraint-hard':   '#ef4444',
    '--constraint-medium': '#ea580c',
    '--constraint-soft':   '#3b82f6',
    // Phase indicator badges (brand-aligned)
    '--phase-ch-bg':            'rgba(234,88,12,0.15)',
    '--phase-ch-text':          '#c2410c',
    '--phase-ls-compact-bg':    'rgba(59,130,246,0.15)',
    '--phase-ls-compact-text':  '#2563eb',
    '--phase-ls-conflict-bg':   'rgba(34,197,94,0.15)',
    '--phase-ls-conflict-text': '#16a34a',
    '--phase-ls-room-bg':       'rgba(168,85,247,0.15)',
    '--phase-ls-room-text':     '#9333ea',
    '--phase-pre-assign-bg':    'rgba(20,184,166,0.15)',
    '--phase-pre-assign-text':  '#0d9488',
  },
};

const ThemeCtx = createContext<{ mode: Mode; setMode: (m: Mode) => void }>({
  mode: 'light', setMode: () => {},
});

export function ThemeProvider({ children }: { children: ReactNode }) {
  const [mode, setMode] = useState<Mode>(
    () => (localStorage.getItem('theme') as Mode) || 'light'
  );

  useEffect(() => {
    const vars = themes[mode];
    const root = document.documentElement;
    // Clear ALL theme variables first (prevent stale values from previous theme)
    for (const theme of Object.values(themes)) {
      for (const k of Object.keys(theme)) {
        root.style.removeProperty(k);
      }
    }
    // Apply new theme
    for (const [k, v] of Object.entries(vars)) {
      root.style.setProperty(k, v);
    }
    document.body.style.background = vars['--bg'];
    document.body.style.color = vars['--text'];
    localStorage.setItem('theme', mode);
  }, [mode]);

  return <ThemeCtx.Provider value={{ mode, setMode }}>{children}</ThemeCtx.Provider>;
}

export function useTheme() { return useContext(ThemeCtx); }
