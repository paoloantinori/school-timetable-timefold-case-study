/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{js,ts,jsx,tsx}'],
  theme: {
    extend: {
      colors: {
        // Background surfaces (theme-aware via CSS variables)
        surface: 'var(--surface)',
        'surface-alt': 'var(--surface-alt)',
        'surface-hover': 'var(--surface-hover)',
        base: 'var(--bg)',
        // Borders
        border: 'var(--border)',
        'border-light': 'var(--border-light)',
        'border-faint': 'var(--border-faint)',
        // Text colors
        content: 'var(--text)',
        muted: 'var(--text-secondary)',
        faint: 'var(--text-muted)',
        // Accent colors (theme-aware)
        accent: 'var(--active-link-bg)',
        'accent-text': 'var(--active-link)',
        // Semantic colors
        action: 'var(--action-bg)',
        'action-text': 'var(--action-text)',
        danger: 'var(--delete-bg)',
        // Schedule-specific
        'day-header': 'var(--day-header-bg)',
        header: 'var(--header-bg)',
        corner: 'var(--corner-bg)',
        // Navigation
        'nav-bg': 'var(--nav-bg)',
        'nav-text': 'var(--nav-text)',
        'nav-hover': 'var(--nav-hover)',
        // Elapsed time
        elapsed: 'var(--elapsed-text)',
        // Holiday
        holiday: 'var(--holiday-bg)',
        // Stage day (amber/yellow theme)
        'stage-bg': 'var(--stage-bg)',
        'stage-border': 'var(--stage-border)',
        'stage-highlight': 'var(--stage-highlight)',
        // Input
        'input-border': 'var(--input-border)',
        // Header text
        'header-text': 'var(--header-text, var(--text))',
        // Feedback banners
        'feedback-error-bg': 'var(--feedback-error-bg)',
        'feedback-error-border': 'var(--feedback-error-border)',
        'feedback-error-text': 'var(--feedback-error-text)',
        'feedback-success-bg': 'var(--feedback-success-bg)',
        'feedback-success-border': 'var(--feedback-success-border)',
        'feedback-success-text': 'var(--feedback-success-text)',
        'feedback-warning-bg': 'var(--feedback-warning-bg)',
        'feedback-warning-border': 'var(--feedback-warning-border)',
        'feedback-warning-text': 'var(--feedback-warning-text)',
        // Danger button
        'danger-bg': 'var(--danger-bg)',
        'danger-border': 'var(--danger-border)',
        'danger-text': 'var(--danger-text)',
        'danger-hover-bg': 'var(--danger-hover-bg)',
        // Constraint level accents
        'constraint-hard': 'var(--constraint-hard)',
        'constraint-medium': 'var(--constraint-medium)',
        'constraint-soft': 'var(--constraint-soft)',
        // Phase indicators
        'phase-ch-bg': 'var(--phase-ch-bg)',
        'phase-ch-text': 'var(--phase-ch-text)',
        'phase-ls-compact-bg': 'var(--phase-ls-compact-bg)',
        'phase-ls-compact-text': 'var(--phase-ls-compact-text)',
        'phase-ls-conflict-bg': 'var(--phase-ls-conflict-bg)',
        'phase-ls-conflict-text': 'var(--phase-ls-conflict-text)',
        'phase-ls-room-bg': 'var(--phase-ls-room-bg)',
        'phase-ls-room-text': 'var(--phase-ls-room-text)',
        'phase-pre-assign-bg': 'var(--phase-pre-assign-bg)',
        'phase-pre-assign-text': 'var(--phase-pre-assign-text)',
      },
      fontFamily: {
        sans: ['Inter var', 'Inter', 'system-ui', 'sans-serif'],
        mono: ['JetBrains Mono', 'Consolas', 'Monaco', 'monospace'],
      },
      boxShadow: {
        'soft': '0 1px 3px var(--shadow)',
        'elevated': '0 4px 12px var(--shadow)',
      },
      spacing: {
        '4.5': '1.125rem',
        '15': '3.75rem',
      },
      // ScheduleTimeline column widths
      width: {
        'time-col': '95px',
        'subject-col': '85px',
        'teacher-col': '120px',
        'room-col': '80px',
        'group-col': '100px',
        'sidebar': '280px',
      },
      minWidth: {
        'time-col': '95px',
        'subject-col': '85px',
        'group-col': '100px',
      },
      maxWidth: {
        'time-col': '95px',
        'subject-col': '85px',
        'group-col': '100px',
        'subject-cell': '75px',
        'teacher-cell': '120px',
        'room-cell': '80px',
        'group-cell': '90px',
        'modal-sm': '300px',
        'modal-md': '380px',
        'modal-lg': '500px',
        'panel': '900px',
      },
      // Z-index for sticky layering
      zIndex: {
        '5': '5',
        '10': '10',
        '20': '20',
        '30': '30',
        'dropdown': '100',
        'modal': '1000',
        'toast': '1050',
      },
      // Custom top position for sub-header row
      top: {
        '28px': '28px',
      },
      maxHeight: {
        'sidebar-scroll': '400px',
      },
      // Custom background patterns
      backgroundImage: {
        'unavailable-stripes': 'repeating-linear-gradient(45deg, var(--surface-hover), var(--surface-hover) 5px, var(--border-light) 5px, var(--border-light) 10px)',
        'stage-gradient': 'linear-gradient(180deg, var(--stage-bg) 0%, var(--stage-highlight) 100%)',
      },
    },
  },
  plugins: [],
};
