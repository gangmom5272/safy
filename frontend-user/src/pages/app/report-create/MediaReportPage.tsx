import { type ChangeEvent, useCallback, useEffect, useRef, useState } from 'react'
import { PageHeader } from '../../../components/common/PageHeader'
import { mockLocations } from '../../../mocks/locations'
import { resolveLocation } from '../home/homeApi'
import { ConfirmReportModal } from './ConfirmReportModal'
import {
  DEFAULT_REPORT_CATEGORY,
  MAX_PHOTO_SIZE_BYTES,
  REPORT_CATEGORY_LABELS,
} from './reportConstants'
import type { ReportFormData } from './reportTypes'
import { createReport } from './reportApi'
import { buildCreateReportRequest } from './reportMapper'
import { uploadImage } from './mediaApi'
import './report-create.css'

type PhotoSource = 'camera' | 'gallery'

const ALLOWED_IMAGE_TYPES = new Set(['image/jpeg', 'image/png', 'image/webp'])
const MAX_PHOTO_COUNT = 5

type PhotoCameraStatus =
  | 'idle'
  | 'connecting'
  | 'ready'
  | 'denied'
  | 'not-found'
  | 'not-readable'
  | 'overconstrained'
  | 'unsupported'
  | 'security'
  | 'aborted'
  | 'error'

interface SelectedPhoto {
  source: PhotoSource
  file: File
  previewUrl: string
}

interface CapturedPhoto {
  file: File
  previewUrl: string
}

interface MediaReportPageProps {
  formData?: ReportFormData
  onBack?: () => void
  onComplete?: (formData: ReportFormData) => void
}

const fallbackFormData: ReportFormData = {
  method: 'photo',
  category: DEFAULT_REPORT_CATEGORY,
  selectedTags: [],
  content: '',
  location: null,
}

function formatFileSize(bytes: number): string {
  return `${(bytes / 1024 / 1024).toFixed(1)}MB`
}

function getCameraErrorStatus(error: unknown): PhotoCameraStatus {
  if (!(error instanceof DOMException)) {
    return 'error'
  }

  if (error.name === 'NotAllowedError' || error.name === 'PermissionDeniedError') {
    return 'denied'
  }

  if (error.name === 'NotFoundError' || error.name === 'DevicesNotFoundError') {
    return 'not-found'
  }

  if (error.name === 'NotReadableError') {
    return 'not-readable'
  }

  if (error.name === 'OverconstrainedError') {
    return 'overconstrained'
  }

  if (error.name === 'SecurityError') {
    return 'security'
  }

  if (error.name === 'AbortError') {
    return 'aborted'
  }

  return 'error'
}

function getCameraMessage(status: PhotoCameraStatus): string {
  switch (status) {
    case 'idle':
      return '카메라 연결 전입니다.'
    case 'connecting':
      return '카메라를 연결하는 중입니다.'
    case 'ready':
      return '카메라 미리보기 준비 완료'
    case 'denied':
      return '카메라 권한이 거부되었습니다. 브라우저 설정에서 카메라 권한을 허용해주세요.'
    case 'not-found':
      return '사용 가능한 카메라 장치를 찾지 못했습니다.'
    case 'not-readable':
      return '다른 프로그램에서 카메라를 사용 중이거나 장치를 열 수 없습니다.'
    case 'overconstrained':
      return '요청한 카메라 조건을 사용할 수 없습니다.'
    case 'unsupported':
      return '이 브라우저는 카메라 접근 기능을 지원하지 않습니다.'
    case 'security':
      return '보안 환경 문제로 카메라를 사용할 수 없습니다. localhost 또는 HTTPS에서 다시 시도해주세요.'
    case 'aborted':
      return '카메라 연결이 중단되었습니다. 다시 시도해주세요.'
    case 'error':
      return '카메라 연결 중 알 수 없는 오류가 발생했습니다.'
  }
}

async function requestPhotoCameraStream(): Promise<MediaStream> {
  try {
    return await navigator.mediaDevices.getUserMedia({
      video: {
        facingMode: { ideal: 'environment' },
      },
      audio: false,
    })
  } catch (error) {
    if (error instanceof DOMException && error.name === 'OverconstrainedError') {
      return navigator.mediaDevices.getUserMedia({ video: true, audio: false })
    }

    throw error
  }
}

function createCapturedPhotoName(): string {
  const timestamp = new Date().toISOString().replace(/[:.]/g, '-')

  return `camera-report-${timestamp}.jpg`
}

export function MediaReportPage({
  formData = fallbackFormData,
  onBack,
  onComplete,
}: MediaReportPageProps) {
  const inputRef = useRef<HTMLInputElement | null>(null)
  const videoRef = useRef<HTMLVideoElement | null>(null)
  const streamRef = useRef<MediaStream | null>(null)
  const cameraRequestInProgressRef = useRef(false)
  const cameraRequestTokenRef = useRef(0)
  const cameraModeActiveRef = useRef(false)
  const captureTokenRef = useRef(0)
  const mountedRef = useRef(false)
  const submitInProgressRef = useRef(false)
  const createdReportIdRef = useRef<number | null>(formData.reportId ?? null)
  const selectedPhotosRef = useRef<SelectedPhoto[]>([])
  const capturedPhotoRef = useRef<CapturedPhoto | null>(null)
  const [selectedPhotos, setSelectedPhotos] = useState<SelectedPhoto[]>([])
  const [capturedPhoto, setCapturedPhoto] = useState<CapturedPhoto | null>(null)
  const [cameraStatus, setCameraStatus] = useState<PhotoCameraStatus>('idle')
  const [errorMessage, setErrorMessage] = useState('')
  const [isConfirmOpen, setIsConfirmOpen] = useState(false)
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [locationName, setLocationName] = useState(formData.location ? '위치명을 확인하는 중입니다.' : '위치 정보 없음')

  useEffect(() => {
    if (!formData.location) return

    let isCurrent = true
    const { latitude, longitude } = formData.location
    const fallbackLocationName = getNearestMockLocationName(latitude, longitude)
    void resolveLocation(latitude, longitude)
      .then((resolvedLocation) => {
        if (isCurrent) setLocationName(resolvedLocation.areaName || fallbackLocationName)
      })
      .catch(() => {
        if (isCurrent) setLocationName(fallbackLocationName)
      })

    return () => {
      isCurrent = false
    }
  }, [formData.location])

  const revokePhotoUrl = useCallback((photo: { previewUrl: string } | null) => {
    if (photo) {
      URL.revokeObjectURL(photo.previewUrl)
    }
  }, [])

  const clearFileInput = () => {
    if (inputRef.current) {
      inputRef.current.value = ''
    }
  }

  const cleanupCamera = useCallback((resetRequestFlag = true) => {
    if (streamRef.current) {
      streamRef.current.getTracks().forEach((track) => track.stop())
      streamRef.current = null
    }

    if (videoRef.current) {
      videoRef.current.srcObject = null
    }

    if (resetRequestFlag) {
      cameraRequestInProgressRef.current = false
    }
  }, [])

  const invalidateCameraWork = useCallback(() => {
    cameraModeActiveRef.current = false
    cameraRequestTokenRef.current += 1
    captureTokenRef.current += 1
  }, [])

  const clearCapturedPhoto = useCallback(() => {
    setCapturedPhoto((currentPhoto) => {
      revokePhotoUrl(currentPhoto)
      return null
    })
  }, [revokePhotoUrl])

  const clearSelectedPhotos = useCallback(() => {
    setSelectedPhotos((currentPhotos) => {
      currentPhotos.forEach(revokePhotoUrl)
      return []
    })
    clearFileInput()
  }, [revokePhotoUrl])

  const resetPhotoState = useCallback(() => {
    clearSelectedPhotos()
    clearCapturedPhoto()
    setErrorMessage('')
    setCameraStatus('idle')
  }, [clearCapturedPhoto, clearSelectedPhotos])

  const startPhotoCamera = useCallback(async () => {
    if (cameraRequestInProgressRef.current || streamRef.current) {
      return
    }

    clearCapturedPhoto()
    setErrorMessage('')
    cameraModeActiveRef.current = true
    const requestToken = cameraRequestTokenRef.current + 1
    cameraRequestTokenRef.current = requestToken

    if (!navigator.mediaDevices?.getUserMedia) {
      setCameraStatus('unsupported')
      return
    }

    cameraRequestInProgressRef.current = true
    setCameraStatus('connecting')

    try {
      const stream = await requestPhotoCameraStream()
      cameraRequestInProgressRef.current = false

      if (
        !mountedRef.current ||
        !cameraModeActiveRef.current ||
        requestToken !== cameraRequestTokenRef.current
      ) {
        stream.getTracks().forEach((track) => track.stop())
        return
      }

      streamRef.current = stream

      if (videoRef.current) {
        videoRef.current.srcObject = stream
      }

      setCameraStatus('ready')
    } catch (error) {
      cameraRequestInProgressRef.current = false

      if (
        !mountedRef.current ||
        !cameraModeActiveRef.current ||
        requestToken !== cameraRequestTokenRef.current
      ) {
        return
      }

      cleanupCamera()
      setCameraStatus(getCameraErrorStatus(error))
    }
  }, [cleanupCamera, clearCapturedPhoto])

  const switchToGallery = () => {
    invalidateCameraWork()
    cleanupCamera()
    clearCapturedPhoto()
    setCameraStatus('idle')
    setErrorMessage('')
    inputRef.current?.click()
  }

  const cancelCamera = () => {
    invalidateCameraWork()
    cleanupCamera()
    clearCapturedPhoto()
    setCameraStatus('idle')
    setErrorMessage('')
  }

  const capturePhoto = () => {
    const video = videoRef.current

    if (!video || video.videoWidth === 0 || video.videoHeight === 0) {
      setErrorMessage('카메라 화면이 아직 준비되지 않았습니다. 잠시 후 다시 촬영해주세요.')
      return
    }

    const canvas = document.createElement('canvas')
    canvas.width = video.videoWidth
    canvas.height = video.videoHeight

    const context = canvas.getContext('2d')

    if (!context) {
      setErrorMessage('사진을 캡처하지 못했습니다. 다시 시도해주세요.')
      return
    }

    context.drawImage(video, 0, 0, canvas.width, canvas.height)
    const captureToken = captureTokenRef.current + 1
    captureTokenRef.current = captureToken
    clearCapturedPhoto()
    canvas.toBlob(
      (blob) => {
        if (
          !mountedRef.current ||
          !cameraModeActiveRef.current ||
          captureToken !== captureTokenRef.current
        ) {
          return
        }

        if (!blob) {
          setErrorMessage('사진을 캡처하지 못했습니다. 다시 시도해주세요.')
          return
        }

        const file = new File([blob], createCapturedPhotoName(), { type: 'image/jpeg' })
        const previewUrl = URL.createObjectURL(file)
        setCapturedPhoto({ file, previewUrl })
        setErrorMessage('')
      },
      'image/jpeg',
      0.9,
    )
  }

  const retakePhoto = () => {
    clearCapturedPhoto()
    setErrorMessage('')

    if (!streamRef.current) {
      startPhotoCamera()
    }
  }

  const useCapturedPhoto = () => {
    if (!capturedPhoto) {
      return
    }

    if (selectedPhotos.length >= MAX_PHOTO_COUNT) {
      setErrorMessage(`이미지는 최대 ${MAX_PHOTO_COUNT}장까지 첨부할 수 있습니다.`)
      return
    }

    invalidateCameraWork()
    setSelectedPhotos((currentPhotos) => [...currentPhotos, {
      source: 'camera', file: capturedPhoto.file, previewUrl: capturedPhoto.previewUrl,
    }])
    setCapturedPhoto(null)
    cleanupCamera()
    setCameraStatus('idle')
    setErrorMessage('')
  }

  const handleFileChange = (event: ChangeEvent<HTMLInputElement>) => {
    const files = Array.from(event.target.files ?? [])
    invalidateCameraWork()
    cleanupCamera()
    clearCapturedPhoto()
    setCameraStatus('idle')
    setErrorMessage('')

    if (!files.length) {
      return
    }

    if (files.some((file) => !ALLOWED_IMAGE_TYPES.has(file.type.toLowerCase()))) {
      setErrorMessage('JPEG, PNG, WEBP 이미지만 선택할 수 있습니다.')
      return
    }

    if (files.some((file) => file.size > MAX_PHOTO_SIZE_BYTES)) {
      setErrorMessage('10MB 이하의 이미지 파일만 선택할 수 있습니다.')
      return
    }

    const remainingCount = MAX_PHOTO_COUNT - selectedPhotos.length
    if (files.length > remainingCount) {
      setErrorMessage(`이미지는 최대 ${MAX_PHOTO_COUNT}장까지 첨부할 수 있습니다.`)
      return
    }

    setSelectedPhotos((currentPhotos) => [
      ...currentPhotos,
      ...files.map((file) => ({ source: 'gallery' as const, file, previewUrl: URL.createObjectURL(file) })),
    ])
    clearFileInput()
  }

  const removeSelectedPhoto = (index: number) => {
    setSelectedPhotos((currentPhotos) => {
      revokePhotoUrl(currentPhotos[index])
      return currentPhotos.filter((_, photoIndex) => photoIndex !== index)
    })
    setErrorMessage('')
  }

  const requestPhotoSubmit = () => {
    if (!selectedPhotos.length || isConfirmOpen) {
      setErrorMessage('사진을 먼저 선택하거나 촬영해주세요.')
      return
    }

    setIsConfirmOpen(true)
  }

  const confirmPhotoSubmit = useCallback(async () => {
    if (!selectedPhotos.length || submitInProgressRef.current) {
      return
    }

    submitInProgressRef.current = true
    setIsSubmitting(true)
    cleanupCamera()
    setErrorMessage('')
    try {
      const reportId = createdReportIdRef.current ?? (await createReport(
        buildCreateReportRequest({ ...formData, method: 'photo' }),
      )).reportId
      createdReportIdRef.current = reportId
      for (const photo of selectedPhotos) {
        await uploadImage(reportId, photo.file)
      }
      onComplete?.({
        ...formData,
        method: 'photo',
        reportId,
        mediaFiles: selectedPhotos.map((photo) => photo.file),
      })
    } catch (error) {
      submitInProgressRef.current = false
      setIsSubmitting(false)
      setIsConfirmOpen(false)
      setErrorMessage(error instanceof Error ? error.message : '제보를 등록하지 못했습니다.')
    }
  }, [cleanupCamera, formData, onComplete, selectedPhotos])

  const closeConfirmModal = useCallback(() => {
    if (isSubmitting) {
      return
    }

    setIsConfirmOpen(false)
  }, [isSubmitting])

  const handleBack = () => {
    setIsConfirmOpen(false)
    invalidateCameraWork()
    cleanupCamera()
    resetPhotoState()
    onBack?.()
  }

  useEffect(() => {
    mountedRef.current = true

    return () => {
      mountedRef.current = false
      invalidateCameraWork()
      cleanupCamera(false)
      selectedPhotosRef.current.forEach(revokePhotoUrl)
      revokePhotoUrl(capturedPhotoRef.current)
    }
  }, [cleanupCamera, invalidateCameraWork, revokePhotoUrl])

  useEffect(() => {
    selectedPhotosRef.current = selectedPhotos
  }, [selectedPhotos])

  useEffect(() => {
    capturedPhotoRef.current = capturedPhoto
  }, [capturedPhoto])

  const categoryLabel = formData.category ? REPORT_CATEGORY_LABELS[formData.category] : '선택 없음'
  const cameraMessage = getCameraMessage(cameraStatus)

  return (
    <section className="report-create-page report-media-page" aria-labelledby="media-report-title">
      <PageHeader title="사진 제보" titleId="media-report-title" backLabel="제보 방식 선택으로 돌아가기" onBack={handleBack} />
      <p className="report-media-intro">카메라로 현장을 촬영하거나 갤러리에서 사진을 선택해주세요.</p>

      <section className="report-create-card" aria-labelledby="media-summary-title">
        <h2 id="media-summary-title">제보 정보</h2>
        <div className="report-media-summary-badges">
          <span className="report-media-category-badge">{categoryLabel}</span>
          <span className="report-media-situation-badge">
            {formData.selectedTags.length > 0 ? formData.selectedTags.join(', ') : '선택 없음'}
          </span>
        </div>
        <div className="report-media-location">
          <span><MediaIcon name="location" />위치</span>
          <strong>{locationName}</strong>
        </div>
        <div className="report-media-content">
          <span><MediaIcon name="message" />제보 내용</span>
          <p>{formData.content}</p>
        </div>
      </section>

      <section className="report-create-card" aria-labelledby="photo-input-title">
        <h2 id="photo-input-title">사진 등록</h2>
        <input
          ref={inputRef}
          id="report-photo-input"
          className="report-file-input"
          type="file"
          multiple
          accept="image/jpeg,image/png,image/webp"
          onChange={handleFileChange}
        />

        {selectedPhotos.length < MAX_PHOTO_COUNT ? (
          <div className="report-photo-mode-grid">
            <button
              type="button"
              className={`report-photo-mode-button ${cameraStatus !== 'idle' ? 'selected' : ''}`}
              disabled={cameraStatus === 'connecting'}
              onClick={startPhotoCamera}
            >
              <MediaIcon name="camera" />카메라로 촬영
            </button>
            <button type="button" className="report-photo-mode-button" onClick={switchToGallery}>
              <MediaIcon name="gallery" />갤러리에서 선택
            </button>
          </div>
        ) : null}

        {errorMessage ? <p className="report-error-text">{errorMessage}</p> : null}

        {cameraStatus !== 'idle' ? (
          <div className="report-camera-box">
            {cameraStatus === 'ready' ? (
              <>
                {capturedPhoto ? (
                  <div className="report-media-preview">
                    <img src={capturedPhoto.previewUrl} alt="카메라로 촬영한 제보 사진 미리보기" />
                    <p>
                      카메라 촬영 · {capturedPhoto.file.name} · {formatFileSize(capturedPhoto.file.size)}
                    </p>
                    <div className="report-inline-actions">
                      <button type="button" className="report-secondary-button" onClick={retakePhoto}>
                        다시 촬영
                      </button>
                      <button type="button" className="report-primary-button" onClick={useCapturedPhoto}>
                        이 사진 사용
                      </button>
                    </div>
                  </div>
                ) : (
                  <>
                    <video ref={videoRef} className="report-photo-camera-video" autoPlay playsInline muted />
                    <div className="report-inline-actions">
                      <button type="button" className="report-ghost-button" onClick={cancelCamera}>
                        카메라 취소
                      </button>
                      <button type="button" className="report-primary-button" onClick={capturePhoto}>
                        사진 촬영
                      </button>
                    </div>
                  </>
                )}
              </>
            ) : (
              <div className="report-camera-state" role={cameraStatus === 'connecting' ? 'status' : 'alert'}>
                <strong>{cameraMessage}</strong>
                {cameraStatus !== 'connecting' ? (
                  <div className="report-inline-actions">
                    <button type="button" className="report-secondary-button" onClick={startPhotoCamera}>
                      다시 시도
                    </button>
                    <button type="button" className="report-ghost-button" onClick={switchToGallery}>
                      갤러리에서 선택
                    </button>
                  </div>
                ) : null}
              </div>
            )}
          </div>
        ) : null}

        {selectedPhotos.length ? (
          <div className="report-media-preview-grid" aria-label={`선택한 이미지 ${selectedPhotos.length}장`}>
            {selectedPhotos.map((photo, index) => (
              <div className="report-media-preview" key={photo.previewUrl}>
                <img src={photo.previewUrl} alt={`선택한 제보 사진 ${index + 1}`} />
                <span className="report-info-badge">{index + 1} · {photo.source === 'camera' ? '카메라 촬영' : '갤러리 선택'}</span>
                <p>{photo.file.name} · {formatFileSize(photo.file.size)}</p>
                <button type="button" className="report-photo-delete-button" onClick={() => removeSelectedPhoto(index)}>삭제</button>
              </div>
            ))}
          </div>
        ) : (
          <p className="report-create-muted">장당 10MB 이하의 이미지를 최대 5장까지 선택하거나 촬영해주세요.</p>
        )}

        <div className="report-create-actions">
          <button
            type="button"
            className="report-primary-button"
            disabled={!selectedPhotos.length || isSubmitting}
            onClick={requestPhotoSubmit}
          >
            사진 제출하기
          </button>
        </div>
      </section>

      {isConfirmOpen ? (
        <ConfirmReportModal
          title={`선택한 사진 ${selectedPhotos.length}장으로 제보하시겠습니까?`}
          description="모든 사진에 실제 현장 상황이 정확하게 담겼는지 확인해주세요. 선택한 순서대로 등록됩니다."
          isSubmitting={isSubmitting}
          onCancel={closeConfirmModal}
          onConfirm={confirmPhotoSubmit}
        />
      ) : null}
    </section>
  )
}

function MediaIcon({ name }: { name: 'back' | 'location' | 'message' | 'camera' | 'gallery' }) {
  const paths = {
    back: <path d="m15 18-6-6 6-6" />,
    location: <><path d="M20 10c0 5-8 11-8 11S4 15 4 10a8 8 0 1 1 16 0Z" /><circle cx="12" cy="10" r="2.5" /></>,
    message: <><path d="M4 5h16v12H8l-4 3V5Z" /><path d="M8 9h8M8 13h5" /></>,
    camera: <><path d="M8 6 9.5 4h5L16 6h3a2 2 0 0 1 2 2v10H3V8a2 2 0 0 1 2-2h3Z" /><circle cx="12" cy="12" r="3" /></>,
    gallery: <><rect x="3" y="4" width="18" height="16" rx="2" /><circle cx="8.5" cy="9" r="1.5" /><path d="M21 15l-5-5L5 20" /></>,
  }
  return <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">{paths[name]}</svg>
}

function getNearestMockLocationName(latitude: number, longitude: number) {
  const nearestLocation = mockLocations.reduce((nearest, candidate) => {
    const nearestDistance = (nearest.latitude - latitude) ** 2 + (nearest.longitude - longitude) ** 2
    const candidateDistance = (candidate.latitude - latitude) ** 2 + (candidate.longitude - longitude) ** 2
    return candidateDistance < nearestDistance ? candidate : nearest
  })
  const distance = (nearestLocation.latitude - latitude) ** 2 + (nearestLocation.longitude - longitude) ** 2
  return distance <= 0.0025 ? nearestLocation.areaName : '위치 정보를 확인할 수 없습니다.'
}
