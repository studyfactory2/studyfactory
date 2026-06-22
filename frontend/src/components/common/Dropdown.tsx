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
  const buttonClassName = [
    `${classNamePrefix}-button`,
    open ? 'open' : '',
    placeholderClass ? 'placeholder' : '',
  ]
    .filter(Boolean)
    .join(' ');

  return (
    <div className={classNamePrefix}>
      <button
        className={buttonClassName}
        type="button"
        disabled={disabled}
        aria-label={selectedOption.label}
        aria-expanded={open}
        aria-haspopup="listbox"
        data-testid={testId}
        onClick={onToggle}
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
