export type ReportStep = 'select' | 'live' | 'media' | 'complete'

export type ReportMethod = 'live' | 'photo'

export type ReportCategory = 'crowd' | 'accident' | 'weather' | 'facility' | 'etc'

export interface ReportLocation {
  latitude: number
  longitude: number
}

export interface ReportFormData {
  method: ReportMethod
  category: ReportCategory
  location: ReportLocation | null
}

export type LocationStatus =
  | 'idle'
  | 'checking'
  | 'success'
  | 'denied'
  | 'timeout'
  | 'unsupported'
  | 'error'
