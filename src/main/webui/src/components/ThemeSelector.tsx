import { useState, useRef, useEffect, useCallback } from 'react';
import { useTheme, type Mode } from '../theme';

interface ThemeOption {
  id: Mode;
  label: string;
  description: string;
  swatch: string[];
}

const themeOptions: ThemeOption[] = [
  {
    id: 'light',
    label: 'Light',
    description: 'Clean and bright',
    swatch: ['#f5f5f5', '#1a73e8', '#333333'],
  },
  {
    id: 'dark',
    label: 'Dark',
    description: 'True black OLED',
    swatch: ['#000000', '#c084fc', '#f5f5f5'],
  },
  {
    id: 'school',
    label: 'school',
    description: 'Brand orange & navy',
    swatch: ['#ffffff', '#e84e0f', '#003366'],
  },
];

function ColorSwatch({ colors, size = 20 }: { colors: string[]; size?: number }) {
  return (
    <div className="flex rounded overflow-hidden shadow-soft">
      {colors.map((color, i) => (
        <div
          key={i}
          style={{
            width: size,
            height: size,
            backgroundColor: color,
          }}
          className={i > 0 ? 'border-l border-white/10' : ''}
        />
      ))}
    </div>
  );
}

export default function ThemeSelector() {
  const { mode, setMode } = useTheme();
  const [isOpen, setIsOpen] = useState(false);
  const [isTransitioning, setIsTransitioning] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);
  const buttonRef = useRef<HTMLButtonElement>(null);
  const [focusedIndex, setFocusedIndex] = useState<number | null>(null);

  const currentTheme = themeOptions.find((t) => t.id === mode) || themeOptions[0];

  useEffect(() => {
    function handleClickOutside(e: MouseEvent) {
      if (containerRef.current && !containerRef.current.contains(e.target as Node)) {
        setIsOpen(false);
        setFocusedIndex(null);
      }
    }
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  const handleSelect = useCallback(
    (themeId: Mode) => {
      if (themeId === mode) {
        setIsOpen(false);
        return;
      }

      setIsTransitioning(true);

      setTimeout(() => {
        setMode(themeId);
        setIsOpen(false);
        setFocusedIndex(null);

        setTimeout(() => {
          setIsTransitioning(false);
        }, 150);
      }, 100);
    },
    [mode, setMode]
  );

  const handleKeyDown = (e: React.KeyboardEvent) => {
    switch (e.key) {
      case 'Enter':
      case ' ':
        e.preventDefault();
        if (isOpen && focusedIndex !== null) {
          handleSelect(themeOptions[focusedIndex].id);
        } else {
          setIsOpen(!isOpen);
          setFocusedIndex(themeOptions.findIndex((t) => t.id === mode));
        }
        break;
      case 'Escape':
        setIsOpen(false);
        setFocusedIndex(null);
        buttonRef.current?.focus();
        break;
      case 'ArrowDown':
        e.preventDefault();
        if (!isOpen) {
          setIsOpen(true);
          setFocusedIndex(0);
        } else {
          setFocusedIndex((prev) =>
            prev === null ? 0 : (prev + 1) % themeOptions.length
          );
        }
        break;
      case 'ArrowUp':
        e.preventDefault();
        if (isOpen) {
          setFocusedIndex((prev) =>
            prev === null ? themeOptions.length - 1 : (prev - 1 + themeOptions.length) % themeOptions.length
          );
        }
        break;
      case 'Tab':
        setIsOpen(false);
        setFocusedIndex(null);
        break;
    }
  };

  useEffect(() => {
    const styleId = 'theme-selector-animations';
    if (!document.getElementById(styleId)) {
      const style = document.createElement('style');
      style.id = styleId;
      style.textContent = `
        @keyframes dropdownFadeIn {
          from { opacity: 0; transform: translateY(-4px); }
          to { opacity: 1; transform: translateY(0); }
        }
      `;
      document.head.appendChild(style);
    }
  }, []);

  return (
    <div ref={containerRef} className="relative">
      {isTransitioning && (
        <div
          className="fixed inset-0 bg-base opacity-15 pointer-events-none z-[9999]
                      transition-opacity duration-150"
        />
      )}

      <button
        ref={buttonRef}
        onClick={() => {
          setIsOpen(!isOpen);
          if (!isOpen) {
            setFocusedIndex(themeOptions.findIndex((t) => t.id === mode));
          }
        }}
        onKeyDown={handleKeyDown}
        aria-haspopup="listbox"
        aria-expanded={isOpen}
        aria-label={`Theme: ${currentTheme.label}`}
        className={`flex items-center gap-2.5 px-3 py-1.5 bg-surface border border-border
                    rounded-md cursor-pointer transition-all outline-none min-w-[140px]
                    ${isOpen ? 'border-action-text shadow-[0_0_0_2px_rgba(26,115,232,0.2)]' : ''}`}
      >
        <ColorSwatch colors={currentTheme.swatch} size={18} />
        <span className="text-sm font-medium tracking-wide text-content">
          {currentTheme.label}
        </span>
        <span
          className="text-xs text-muted ml-auto transition-transform duration-200"
          style={{ transform: isOpen ? 'rotate(180deg)' : 'rotate(0deg)' }}
        >
          ▾
        </span>
      </button>

      {isOpen && (
        <div
          role="listbox"
          className="absolute top-[calc(100%+4px)] right-0 bg-surface border border-border
                     rounded-lg shadow-elevated overflow-hidden min-w-[180px] z-dropdown
                     animate-[dropdownFadeIn_0.15s_ease]"
        >
          {themeOptions.map((theme, index) => {
            const isSelected = theme.id === mode;
            const isFocused = index === focusedIndex;

            return (
              <button
                key={theme.id}
                role="option"
                aria-selected={isSelected}
                onClick={() => handleSelect(theme.id)}
                onMouseEnter={() => setFocusedIndex(index)}
                className={`flex items-center gap-3 px-3.5 py-2.5 cursor-pointer
                            transition-colors w-full text-left outline-none border-none
                            bg-transparent
                            ${isFocused || isSelected ? 'bg-surface-hover' : ''}
                            ${isSelected ? 'bg-action' : ''}`}
                tabIndex={-1}
              >
                <ColorSwatch colors={theme.swatch} size={22} />
                <div className="flex-1 flex flex-col gap-0.5">
                  <span className="text-sm font-medium text-content">
                    {theme.label}
                  </span>
                  <span className="text-xs text-muted">
                    {theme.description}
                  </span>
                </div>
                {isSelected && (
                  <span className="text-action-text text-sm opacity-90">✓</span>
                )}
              </button>
            );
          })}
        </div>
      )}
    </div>
  );
}
