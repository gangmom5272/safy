import { useCallback, useEffect, useRef, useState, type CSSProperties, type PointerEvent as ReactPointerEvent } from 'react'

const MIN_SCALE = 1
const MAX_SCALE = 3

type Point = { x: number; y: number }
type Transform = Point & { scale: number }

function distance(a: Point, b: Point) {
  return Math.hypot(a.x - b.x, a.y - b.y)
}

function midpoint(a: Point, b: Point) {
  return { x: (a.x + b.x) / 2, y: (a.y + b.y) / 2 }
}

function clamp(value: number, min: number, max: number) {
  return Math.min(Math.max(value, min), max)
}

export function useMapGestures(resetKey: string | number) {
  const viewportRef = useRef<HTMLDivElement>(null)
  const pointers = useRef(new Map<number, Point>())
  const transformRef = useRef<Transform>({ scale: MIN_SCALE, x: 0, y: 0 })
  const gestureStart = useRef({ transform: { scale: MIN_SCALE, x: 0, y: 0 }, distance: 0, center: { x: 0, y: 0 } })
  const [transform, setTransform] = useState<Transform>({ scale: MIN_SCALE, x: 0, y: 0 })

  const updateTransform = useCallback((next: Transform) => {
    const viewport = viewportRef.current
    if (!viewport || next.scale <= MIN_SCALE) {
      const reset = { scale: MIN_SCALE, x: 0, y: 0 }
      transformRef.current = reset
      setTransform(reset)
      return
    }

    const bounded = {
      scale: next.scale,
      x: clamp(next.x, viewport.clientWidth * (1 - next.scale), 0),
      y: clamp(next.y, viewport.clientHeight * (1 - next.scale), 0),
    }
    transformRef.current = bounded
    setTransform(bounded)
  }, [])

  const startGesture = useCallback(() => {
    const active = [...pointers.current.values()]
    gestureStart.current = {
      transform: transformRef.current,
      distance: active.length >= 2 ? distance(active[0], active[1]) : 0,
      center: active.length >= 2 ? midpoint(active[0], active[1]) : active[0] ?? { x: 0, y: 0 },
    }
  }, [])

  const onPointerDown = useCallback((event: ReactPointerEvent<HTMLDivElement>) => {
    event.currentTarget.setPointerCapture(event.pointerId)
    const bounds = event.currentTarget.getBoundingClientRect()
    pointers.current.set(event.pointerId, { x: event.clientX - bounds.left, y: event.clientY - bounds.top })
    startGesture()
  }, [startGesture])

  const onPointerMove = useCallback((event: ReactPointerEvent<HTMLDivElement>) => {
    if (!pointers.current.has(event.pointerId)) return
    const bounds = event.currentTarget.getBoundingClientRect()
    pointers.current.set(event.pointerId, { x: event.clientX - bounds.left, y: event.clientY - bounds.top })
    const active = [...pointers.current.values()]
    const start = gestureStart.current

    if (active.length >= 2 && start.distance > 0) {
      const center = midpoint(active[0], active[1])
      const scale = clamp(start.transform.scale * distance(active[0], active[1]) / start.distance, MIN_SCALE, MAX_SCALE)
      const contentX = (start.center.x - start.transform.x) / start.transform.scale
      const contentY = (start.center.y - start.transform.y) / start.transform.scale
      updateTransform({ scale, x: center.x - contentX * scale, y: center.y - contentY * scale })
      return
    }

    if (active.length === 1 && start.transform.scale > MIN_SCALE) {
      updateTransform({
        scale: start.transform.scale,
        x: start.transform.x + active[0].x - start.center.x,
        y: start.transform.y + active[0].y - start.center.y,
      })
    }
  }, [updateTransform])

  const finishPointer = useCallback((event: ReactPointerEvent<HTMLDivElement>) => {
    pointers.current.delete(event.pointerId)
    if (event.currentTarget.hasPointerCapture(event.pointerId)) event.currentTarget.releasePointerCapture(event.pointerId)
    startGesture()
  }, [startGesture])

  useEffect(() => {
    pointers.current.clear()
    updateTransform({ scale: MIN_SCALE, x: 0, y: 0 })
  }, [resetKey, updateTransform])

  const contentStyle: CSSProperties = {
    transform: `translate3d(${transform.x}px, ${transform.y}px, 0) scale(${transform.scale})`,
    transformOrigin: '0 0',
  }

  return {
    viewportProps: {
      ref: viewportRef,
      onPointerDown,
      onPointerMove,
      onPointerUp: finishPointer,
      onPointerCancel: finishPointer,
    },
    contentStyle,
    minScale: MIN_SCALE,
    maxScale: MAX_SCALE,
  }
}
