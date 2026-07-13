# 图片上传与处理模块使用指南

## 📋 功能概述

本模块提供了完整的图片上传和处理功能，包括：

- ✅ 图片文件上传（支持 JPG、PNG、GIF、WEBP、BMP）
- ✅ 自动压缩大图片（可配置阈值和质量）
- ✅ EXIF 元数据提取（相机信息、拍摄时间、GPS等）
- ✅ 图片格式转换
- ✅ 缩略图自动生成
- ✅ 图片尺寸调整
- ✅ 图片裁剪
- ✅ 水印添加

## 🔧 配置说明

在 `application.yml` 中配置图片处理参数：

```yaml
image:
  storage-path: ./uploads/images           # 原图存储路径
  thumbnail-path: ./uploads/thumbnails     # 缩略图存储路径
  max-file-size: 10                        # 最大文件大小（MB）
  compress-threshold: 500                  # 压缩阈值（KB）
  compress-quality: 0.7                    # 压缩质量（0.0-1.0）
  thumbnail-width: 200                     # 缩略图宽度（像素）
  thumbnail-height: 200                    # 缩略图高度（像素）
  allowed-formats: jpg,jpeg,png,gif,webp,bmp  # 允许的格式
```

## 📡 API 接口

### 1. 上传图片并自动处理

**接口**: `POST /api/image/upload`

**请求参数**:
- `file`: MultipartFile - 图片文件

**响应示例**:
```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "fileId": "550e8400-e29b-41d4-a716-446655440000",
    "originalFilename": "example.jpg",
    "storagePath": "./uploads/images/550e8400-e29b-41d4-a716-446655440000.jpg",
    "thumbnailPath": "./uploads/thumbnails/550e8400-e29b-41d4-a716-446655440000_thumb.jpg",
    "fileSize": 2048576,
    "compressedSize": 1024288,
    "width": 1920,
    "height": 1080,
    "format": "jpg",
    "compressed": true,
    "compressionRatio": 0.5,
    "uploadTime": "2026-07-13T15:30:00"
  }
}
```

**cURL 示例**:
```bash
curl -X POST http://localhost:8083/api/image/upload \
  -F "file=@/path/to/image.jpg"
```

### 2. 获取图片元数据

**接口**: `GET /api/image/metadata/{fileId}`

**响应示例**:
```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "fileId": "550e8400-e29b-41d4-a716-446655440000",
    "cameraMake": "Canon",
    "cameraModel": "EOS R5",
    "dateTimeOriginal": "2026-07-13T10:30:00",
    "aperture": "f/2.8",
    "shutterSpeed": "1/200",
    "iso": 400,
    "focalLength": 50.0,
    "gpsLatitude": 39.9042,
    "gpsLongitude": 116.4074
  }
}
```

### 3. 下载图片

**接口**: `GET /api/image/download/{fileId}?thumbnail=false`

**参数**:
- `fileId`: 文件ID
- `thumbnail`: 是否下载缩略图（默认 false）

**cURL 示例**:
```bash
# 下载原图
curl -O http://localhost:8083/api/image/download/550e8400-e29b-41d4-a716-446655440000

# 下载缩略图
curl -O http://localhost:8083/api/image/download/550e8400-e29b-41d4-a716-446655440000?thumbnail=true
```

### 4. 图片格式转换

**接口**: `POST /api/image/convert`

**请求参数**:
- `file`: MultipartFile - 图片文件
- `targetFormat`: String - 目标格式（jpg/png/gif/webp）

**cURL 示例**:
```bash
curl -X POST http://localhost:8083/api/image/convert \
  -F "file=@/path/to/image.png" \
  -F "targetFormat=jpg"
```

### 5. 调整图片尺寸

**接口**: `POST /api/image/resize`

**请求参数**:
- `file`: MultipartFile - 图片文件
- `width`: int - 目标宽度
- `height`: int - 目标高度
- `keepAspectRatio`: boolean - 是否保持宽高比（默认 true）

**cURL 示例**:
```bash
curl -X POST http://localhost:8083/api/image/resize \
  -F "file=@/path/to/image.jpg" \
  -F "width=800" \
  -F "height=600" \
  -F "keepAspectRatio=true"
```

### 6. 裁剪图片

**接口**: `POST /api/image/crop`

**请求参数**:
- `file`: MultipartFile - 图片文件
- `x`: int - 起始X坐标
- `y`: int - 起始Y坐标
- `width`: int - 裁剪宽度
- `height`: int - 裁剪高度

**cURL 示例**:
```bash
curl -X POST http://localhost:8083/api/image/crop \
  -F "file=@/path/to/image.jpg" \
  -F "x=100" \
  -F "y=100" \
  -F "width=400" \
  -F "height=300"
```

### 7. 删除图片

**接口**: `DELETE /api/image/{fileId}`

**cURL 示例**:
```bash
curl -X DELETE http://localhost:8083/api/image/550e8400-e29b-41d4-a716-446655440000
```

## 💻 代码调用示例

### Java 客户端调用

```java
// 上传图片
RestTemplate restTemplate = new RestTemplate();
MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
body.add("file", new FileSystemResource("/path/to/image.jpg"));

HttpHeaders headers = new HttpHeaders();
headers.setContentType(MediaType.MULTIPART_FORM_DATA);

HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

ResponseEntity<Result> response = restTemplate.postForEntity(
    "http://localhost:8083/api/image/upload",
    requestEntity,
    Result.class
);

System.out.println(response.getBody());
```

### 前端 JavaScript 调用

```javascript
// 上传图片
async function uploadImage(file) {
  const formData = new FormData();
  formData.append('file', file);
  
  const response = await fetch('/api/image/upload', {
    method: 'POST',
    body: formData
  });
  
  const result = await response.json();
  console.log(result);
  return result.data;
}

// 下载图片
function downloadImage(fileId, thumbnail = false) {
  const url = `/api/image/download/${fileId}?thumbnail=${thumbnail}`;
  window.open(url, '_blank');
}
```

## 🎯 使用场景

### 知识库图片管理
- 上传知识文档中的配图
- 自动生成缩略图用于列表展示
- 提取图片元数据用于分类检索

### 用户头像上传
- 用户上传头像时自动压缩
- 生成多种尺寸的缩略图
- 统一转换为 WebP 格式优化加载

### 产品图片管理
- 批量上传产品图片
- 自动调整尺寸适配不同展示场景
- 添加品牌水印保护版权

## ⚠️ 注意事项

1. **文件大小限制**: 默认最大 10MB，可在配置中调整
2. **格式验证**: 仅允许配置的格式上传，防止恶意文件
3. **存储路径**: 确保应用有写入权限
4. **并发处理**: 大量图片上传时注意内存和CPU占用
5. **清理机制**: 建议定期清理临时文件和废弃图片

## 🔮 扩展建议

- 集成对象存储（MinIO/阿里云OSS/腾讯云COS）
- 添加图片CDN加速
- 实现图片OCR文字识别
- 支持批量上传和处理
- 添加图片审核功能（涉黄涉暴检测）
