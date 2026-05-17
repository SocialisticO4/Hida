import { useState, useCallback } from "react";

export interface GallerySelection {
  isSelectionMode: boolean;
  selectedPaths: Set<string>;
  toggleSelection: (path: string) => void;
  selectAll: (paths: string[]) => void;
  clearSelection: () => void;
  enterSelectionMode: (path: string) => void;
  selectedCount: number;
}

export function useGallerySelection(): GallerySelection {
  const [selectedPaths, setSelectedPaths] = useState<Set<string>>(new Set());
  const [isSelectionMode, setIsSelectionMode] = useState(false);

  const toggleSelection = useCallback((path: string) => {
    setSelectedPaths((prev) => {
      const next = new Set(prev);
      if (next.has(path)) {
        next.delete(path);
      } else {
        next.add(path);
      }
      if (next.size === 0) {
        setIsSelectionMode(false);
      }
      return next;
    });
  }, []);

  const selectAll = useCallback((paths: string[]) => {
    setSelectedPaths((prev) => {
      if (prev.size === paths.length) {
        setIsSelectionMode(false);
        return new Set();
      }
      return new Set(paths);
    });
  }, []);

  const clearSelection = useCallback(() => {
    setSelectedPaths(new Set());
    setIsSelectionMode(false);
  }, []);

  const enterSelectionMode = useCallback((path: string) => {
    setIsSelectionMode(true);
    setSelectedPaths(new Set([path]));
  }, []);

  return {
    isSelectionMode,
    selectedPaths,
    toggleSelection,
    selectAll,
    clearSelection,
    enterSelectionMode,
    selectedCount: selectedPaths.size,
  };
}
