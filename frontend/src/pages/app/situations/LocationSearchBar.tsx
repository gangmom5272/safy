import { useMemo, useRef, useState, type KeyboardEvent } from 'react'
import type { LocationSearchResult } from './types'

type LocationSearchBarProps = {
  keyword: string
  suggestions: LocationSearchResult[]
  isLoading: boolean
  error: unknown
  onKeywordChange: (keyword: string) => void
  onSelect: (location: LocationSearchResult) => void
  onFilterOpen: () => void
}

export function LocationSearchBar({
  keyword,
  suggestions,
  isLoading,
  error,
  onKeywordChange,
  onSelect,
  onFilterOpen,
}: LocationSearchBarProps) {
  const [activeIndex, setActiveIndex] = useState(-1)
  const [isListOpen, setIsListOpen] = useState(false)
  const hasKeyword = keyword.trim().length > 0
  const showSuggestions = isListOpen && keyword.trim().length >= 2
  const listId = 'location-search-suggestions'
  const inputRef = useRef<HTMLInputElement | null>(null)

  const activeDescendant = useMemo(() => {
    if (activeIndex < 0) {
      return undefined
    }

    return `location-option-${suggestions[activeIndex]?.id}`
  }, [activeIndex, suggestions])

  const handleKeyDown = (event: KeyboardEvent<HTMLInputElement>) => {
    if (!showSuggestions) {
      return
    }

    if (event.key === 'ArrowDown') {
      event.preventDefault()
      setActiveIndex((currentIndex) =>
        Math.min(currentIndex + 1, Math.max(suggestions.length - 1, 0)),
      )
    }

    if (event.key === 'ArrowUp') {
      event.preventDefault()
      setActiveIndex((currentIndex) => Math.max(currentIndex - 1, 0))
    }

    if (event.key === 'Enter' && activeIndex >= 0 && suggestions[activeIndex]) {
      event.preventDefault()
      selectSuggestion(suggestions[activeIndex])
    }

    if (event.key === 'Escape') {
      setIsListOpen(false)
      inputRef.current?.blur()
    }
  }

  const selectSuggestion = (location: LocationSearchResult) => {
    setIsListOpen(false)
    setActiveIndex(-1)
    onSelect(location)
  }

  return (
    <div className="location-search-area">
      <div className="location-search-row">
        <label className="location-search-field">
          <span className="search-icon" aria-hidden="true" />
          <span className="sr-only">지역명, 장소명 검색</span>
          <input
            ref={inputRef}
            type="search"
            value={keyword}
            placeholder="지역명, 장소명 검색"
            aria-label="지역명, 장소명 검색"
            aria-autocomplete="list"
            aria-controls={listId}
            aria-activedescendant={activeDescendant}
            onChange={(event) => {
              setActiveIndex(-1)
              setIsListOpen(true)
              onKeywordChange(event.target.value)
            }}
            onFocus={() => setIsListOpen(true)}
            onKeyDown={handleKeyDown}
          />
          {hasKeyword ? (
            <button
              className="search-clear-button"
              type="button"
              aria-label="검색어 지우기"
              onClick={() => onKeywordChange('')}
            >
              지우기
            </button>
          ) : null}
        </label>
        <button
          className="filter-icon-button"
          type="button"
          aria-label="실시간 제보 필터 열기"
          onClick={onFilterOpen}
        >
          필터
        </button>
      </div>

      {showSuggestions ? (
        <LocationSuggestionList
          id={listId}
          suggestions={suggestions}
          activeIndex={activeIndex}
          isLoading={isLoading}
          error={error}
          onSelect={selectSuggestion}
        />
      ) : null}
    </div>
  )
}

type LocationSuggestionListProps = {
  id: string
  suggestions: LocationSearchResult[]
  activeIndex: number
  isLoading: boolean
  error: unknown
  onSelect: (location: LocationSearchResult) => void
}

export function LocationSuggestionList({
  id,
  suggestions,
  activeIndex,
  isLoading,
  error,
  onSelect,
}: LocationSuggestionListProps) {
  if (isLoading) {
    return (
      <div className="location-suggestions" id={id} role="listbox">
        <p className="suggestion-message">검색 중입니다.</p>
      </div>
    )
  }

  if (error) {
    return (
      <div className="location-suggestions" id={id} role="listbox">
        <p className="suggestion-message">검색 결과를 불러오지 못했습니다.</p>
      </div>
    )
  }

  if (!suggestions.length) {
    return (
      <div className="location-suggestions" id={id} role="listbox">
        <p className="suggestion-message">검색 결과가 없습니다. 다른 지역명이나 장소명을 입력해 주세요.</p>
      </div>
    )
  }

  return (
    <ul className="location-suggestions" id={id} role="listbox">
      {suggestions.map((suggestion, index) => (
        <li key={suggestion.id}>
          <button
            id={`location-option-${suggestion.id}`}
            className={activeIndex === index ? 'active' : undefined}
            type="button"
            role="option"
            aria-selected={activeIndex === index}
            onClick={() => onSelect(suggestion)}
          >
            <strong>{suggestion.name}</strong>
            <span>{suggestion.address}</span>
          </button>
        </li>
      ))}
    </ul>
  )
}
