import api from '@/utils/request'

export function login(data) {
  return api.post('/api/auth/login', data)
}

export function register(data) {
  return api.post('/api/auth/register', data)
}

export function getAnnotations() {
  return api.get('/annotations')
}

export function createAnnotation(data) {
  return api.post('/annotations', data)
}

export function updateAnnotation(id, data) {
  return api.put(`/annotations/${id}`, data)
}

export function deleteAnnotation(id) {
  return api.delete(`/annotations/${id}`)
}

export function getSymbols() {
  return api.get('/symbols')
}

export function uploadSymbol(formData) {
  return api.post('/symbols/upload', formData, {
    headers: {
      'Content-Type': 'multipart/form-data'
    }
  })
}

export function deleteSymbol(id) {
  return api.delete(`/symbols/${id}`)
}
