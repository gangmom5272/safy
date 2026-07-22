import { create } from "zustand";

export interface Location {
  areaId: number;
  areaName: string;
  latitude: number;
  longitude: number;
}

interface LocationState {
  location: Location | null;
  setLocation: (location: Location) => void;
  clearLocation: () => void;
}

export const useLocationStore = create<LocationState>((set) => ({
  location: null,

  setLocation: (location) =>
    set({
      location,
    }),

  clearLocation: () =>
    set({
      location: null,
    }),
}));