import { useEffect, useRef } from 'react';

export type DropdownOption = {
  value: string;
  label: string;
  testId?: string;
};

type DropdownProps = {
  classNamePrefix: 'custom-select' | 'form-dropdown';
  disabled?: boolean;
  label: string;
  open: boolean;
  options: DropdownOption[];
  placeholderClass?: boolean;
  selectedOption: DropdownOption;
  testId?: string;
  onSelect: (value: string) => void;
  onToggle: () => void;
};

export function Dropdown({
  classNamePrefix,
  disabled = false,
  label,
  open,
  options,
  placeholderClass = false,
  selectedOption,
  testId,
  onSelect,
  onToggle,
}: DropdownProps) {
  const dropdownRef = useRef<HTMLDivElement>(null);
  const toggledByPointerRef = useRef(false);

  useEffect(() => {
    if (!open) return;

    const closeOnOutsidePointer = (event: PointerEvent) => {
      if (!dropdownRef.current?.contains(event.target as Node)) {
        onToggle();
      }
    };

    document.addEventListener('pointerdown', closeOnOutsidePointer);
    return () => document.removeEventListener('pointerdown', closeOnOutsidePointer);
  }, [open, onToggle]);

  const buttonClassName = [
    `${classNamePrefix}-button`,
    open ? 'open' : '',
    placeholderClass ? 'placeholder' : '',
  ]
    .filter(Boolean)
    .join(' ');

  return (
    <div className={classNamePrefix} ref={dropdownRef}>
      <button
        className={buttonClassName}
        type="button"
        disabled={disabled}
        aria-label={selectedOption.label}
        aria-expanded={open}
        aria-haspopup="listbox"
        data-testid={testId}
        onPointerDown={() => {
          toggledByPointerRef.current = true;
          onToggle();
        }}
        onClick={() => {
          // 키보드로 활성화된 버튼은 pointerdown을 거치지 않으므로 click에서 연다.
          if (toggledByPointerRef.current) {
            toggledByPointerRef.current = false;
            return;
          }
          onToggle();
        }}
      >
        <span>{selectedOption.label}</span>
      </button>
      {open && (
        <div className={`${classNamePrefix}-menu`} role="listbox" aria-label={label}>
          {options.map((option) => (
            <button
              className={`${classNamePrefix}-option${option.value === selectedOption.value ? ' selected' : ''}`}
              key={option.value}
              type="button"
              role="option"
              aria-selected={option.value === selectedOption.value}
              aria-label={option.label}
              data-testid={option.testId}
              onClick={() => onSelect(option.value)}
            >
              {option.label}
            </button>
          ))}
        </div>
      )}
    </div>
  );
}
