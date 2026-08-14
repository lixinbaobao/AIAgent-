package com.zhangbo.yuaiagent.controller;

import cn.hutool.core.util.IdUtil;
import com.zhangbo.yuaiagent.constant.FileConstant;
import com.zhangbo.yuaiagent.service.KnowledgeBaseService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 文件上传控制器（支持知识库功能）
 */
@RestController
@RequestMapping("/file")
@Slf4j
public class FileController {

    @Resource
    private KnowledgeBaseService knowledgeBaseService;

    /**
     * 上传文件（自动加入知识库）
     *
     * @param file 上传的文件
     * @return 上传结果
     */
    @PostMapping("/upload")
    public Map<String, Object> uploadFile(@RequestParam("file") MultipartFile file) {
        Map<String, Object> result = new HashMap<>();
        try {
            if (file.isEmpty()) {
                result.put("success", false);
                result.put("message", "文件不能为空");
                return result;
            }

            // 获取原始文件名
            String originalFilename = file.getOriginalFilename();
            // 生成新文件名（防止重名）
            String newFilename = IdUtil.simpleUUID() + "_" + originalFilename;
            // 文件保存目录
            String saveDir = FileConstant.FILE_SAVE_DIR + "/upload";
            File dir = new File(saveDir);
            if (!dir.exists()) {
                dir.mkdirs();
            }
            // 完整保存路径
            String savePath = saveDir + "/" + newFilename;
            // 保存文件
            file.transferTo(new File(savePath));

            // 自动添加到知识库
            try {
                knowledgeBaseService.addFileToKnowledgeBase(new File(savePath));
                result.put("knowledgeAdded", true);
            } catch (Exception e) {
                log.warn("文件添加到知识库失败: {}", e.getMessage());
                result.put("knowledgeAdded", false);
                result.put("knowledgeError", e.getMessage());
            }

            result.put("success", true);
            result.put("message", "上传成功，已加入知识库");
            result.put("name", originalFilename);
            result.put("path", "/upload/" + newFilename);
            result.put("size", file.getSize());
            log.info("文件上传成功: {} -> {}", originalFilename, savePath);
        } catch (IOException e) {
            log.error("文件上传失败", e);
            result.put("success", false);
            result.put("message", "上传失败: " + e.getMessage());
        }
        return result;
    }

    /**
     * 获取已上传文件列表
     *
     * @return 文件列表
     */
    @GetMapping("/list")
    public Map<String, Object> listFiles() {
        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> fileList = new ArrayList<>();

        String uploadDir = FileConstant.FILE_SAVE_DIR + "/upload";
        File dir = new File(uploadDir);
        if (dir.exists() && dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isFile()) {
                        Map<String, Object> fileInfo = new HashMap<>();
                        fileInfo.put("name", file.getName());
                        fileInfo.put("size", file.length());
                        fileInfo.put("path", "/upload/" + file.getName());
                        fileInfo.put("lastModified", file.lastModified());
                        fileList.add(fileInfo);
                    }
                }
            }
        }

        result.put("success", true);
        result.put("files", fileList);
        return result;
    }

    /**
     * 删除文件
     *
     * @param path 文件相对路径
     * @return 删除结果
     */
    @DeleteMapping("/delete")
    public Map<String, Object> deleteFile(@RequestParam("path") String path) {
        Map<String, Object> result = new HashMap<>();
        try {
            // 安全校验：防止路径穿越
            if (path.contains("..") || path.contains("//")) {
                result.put("success", false);
                result.put("message", "非法的文件路径");
                return result;
            }

            String fullPath = FileConstant.FILE_SAVE_DIR + path;
            File file = new File(fullPath);
            if (file.exists() && file.isFile()) {
                boolean deleted = file.delete();
                if (deleted) {
                    result.put("success", true);
                    result.put("message", "删除成功");
                    log.info("文件删除成功: {}", fullPath);
                } else {
                    result.put("success", false);
                    result.put("message", "删除失败");
                }
            } else {
                result.put("success", false);
                result.put("message", "文件不存在");
            }
        } catch (Exception e) {
            log.error("文件删除失败", e);
            result.put("success", false);
            result.put("message", "删除失败: " + e.getMessage());
        }
        return result;
    }

    /**
     * 知识库检索
     *
     * @param query 查询问题
     * @param topK  返回结果数量（默认5）
     * @return 相关文档列表
     */
    @GetMapping("/knowledge/search")
    public Map<String, Object> searchKnowledge(
            @RequestParam("query") String query,
            @RequestParam(value = "topK", defaultValue = "5") int topK) {
        Map<String, Object> result = new HashMap<>();
        try {
            List<Document> documents = knowledgeBaseService.search(query, topK);
            List<Map<String, Object>> docList = new ArrayList<>();
            for (Document doc : documents) {
                Map<String, Object> docInfo = new HashMap<>();
                docInfo.put("content", doc.getText());
                docInfo.put("metadata", doc.getMetadata());
                docList.add(docInfo);
            }
            result.put("success", true);
            result.put("query", query);
            result.put("results", docList);
            result.put("count", docList.size());
        } catch (Exception e) {
            log.error("知识库检索失败", e);
            result.put("success", false);
            result.put("message", "检索失败: " + e.getMessage());
        }
        return result;
    }

    /**
     * 清空知识库
     *
     * @return 操作结果
     */
    @PostMapping("/knowledge/clear")
    public Map<String, Object> clearKnowledge() {
        Map<String, Object> result = new HashMap<>();
        try {
            knowledgeBaseService.clear();
            result.put("success", true);
            result.put("message", "知识库已清空");
        } catch (Exception e) {
            log.error("清空知识库失败", e);
            result.put("success", false);
            result.put("message", "清空失败: " + e.getMessage());
        }
        return result;
    }

    /**
     * 重建知识库（重新加载所有已上传文件）
     *
     * @return 操作结果
     */
    @PostMapping("/knowledge/rebuild")
    public Map<String, Object> rebuildKnowledge() {
        Map<String, Object> result = new HashMap<>();
        try {
            knowledgeBaseService.clear();
            // 重新初始化会加载所有文件
            knowledgeBaseService.init();
            result.put("success", true);
            result.put("message", "知识库重建完成");
        } catch (Exception e) {
            log.error("重建知识库失败", e);
            result.put("success", false);
            result.put("message", "重建失败: " + e.getMessage());
        }
        return result;
    }
}
