type RefreshRequest = () => Promise<string>

export function createRefreshCoordinator(request: RefreshRequest) {
  let activeRequest: Promise<string> | null = null

  return function refresh() {
    if (!activeRequest) {
      activeRequest = request().finally(() => {
        activeRequest = null
      })
    }

    return activeRequest
  }
}
