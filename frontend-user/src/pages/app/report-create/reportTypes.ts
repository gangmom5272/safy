export type ReportStep = 'select' | 'live' | 'media' | 'complete'

export type ReportMethod = 'live' | 'photo'

export type ReportCategory = 'crowd' | 'accident' | 'weather' | 'facility' | 'etc'

export type ReportTag = string

export interface ReportLocation {
  latitude: number
  longitude: number
}

export interface ReportFormData {
  method: ReportMethod
  category: ReportCategory | null
  selectedTags: ReportTag[]
  content: string
  location: ReportLocation | null
  reportId?: number
  mediaFile?: File
}

export type LocationStatus =
  | 'idle'
  | 'checking'
  | 'success'
  | 'denied'
  | 'timeout'
  | 'unsupported'
  | 'error'
