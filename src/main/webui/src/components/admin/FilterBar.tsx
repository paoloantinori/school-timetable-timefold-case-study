import { useTranslation } from 'react-i18next';

interface FilterBarProps {
  placeholder?: string;
  value: string;
  onChange: (value: string) => void;
  totalCount: number;
  filteredCount: number;
}

export function FilterBar({ placeholder, value, onChange, totalCount, filteredCount }: FilterBarProps) {
  const { t } = useTranslation('admin');

  return (
    <div className="flex items-center gap-4 mb-4">
      <div className="flex items-center bg-surface-alt border border-border rounded-md
                      px-3 py-2 flex-1 max-w-[300px]">
        <span className="mr-2 text-sm text-muted">{t('search')}</span>
        <input
          type="text"
          placeholder={placeholder || t('filterPlaceholder')}
          value={value}
          onChange={(e) => onChange(e.target.value)}
          className="border-none bg-transparent flex-1 text-sm outline-none
                     text-content min-w-0"
        />
        {value && (
          <button
            onClick={() => onChange('')}
            className="bg-cancel-bg border-none text-muted cursor-pointer
                       text-xs px-2 py-0.5 rounded leading-none"
            aria-label={t('clearFilter')}
          >
            {t('clear')}
          </button>
        )}
      </div>
      <span className="text-xs text-muted whitespace-nowrap">
        {filteredCount === totalCount
          ? `${totalCount} ${t('items')}`
          : `${filteredCount} ${t('of')} ${totalCount}`}
      </span>
    </div>
  );
}
