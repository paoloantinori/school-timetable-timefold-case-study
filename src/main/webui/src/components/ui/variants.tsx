import { cva, type VariantProps } from 'class-variance-authority';

/**
 * Toggle button variants for view mode and group-by toggles
 */
export const toggleButton = cva(
  'px-3 py-2 text-[0.75rem] border rounded cursor-pointer',
  {
    variants: {
      active: {
        true: 'bg-blue-500 text-white border-blue-500',
        false: 'bg-surface text-muted border-input-border',
      },
    },
    defaultVariants: {
      active: false,
    },
  }
);

export type ToggleButtonProps = VariantProps<typeof toggleButton>;

/**
 * Navigation link variants for admin navigation
 */
export const navLink = cva(
  'block px-3 py-2 text-muted no-underline rounded text-sm mb-1',
  {
    variants: {
      active: {
        true: 'bg-accent text-accent-text font-semibold',
        false: 'hover:bg-surface-hover',
      },
    },
    defaultVariants: {
      active: false,
    },
  }
);

export type NavLinkProps = VariantProps<typeof navLink>;

/**
 * Action button variants for table actions
 */
export const actionButton = cva(
  'px-2 py-1 border-none rounded cursor-pointer text-xs mr-1 hover:opacity-80 transition-opacity',
  {
    variants: {
      variant: {
        primary: 'bg-action text-action-text',
        delete: 'bg-danger text-danger-text',
      },
    },
    defaultVariants: {
      variant: 'primary',
    },
  }
);

export type ActionButtonProps = VariantProps<typeof actionButton>;
