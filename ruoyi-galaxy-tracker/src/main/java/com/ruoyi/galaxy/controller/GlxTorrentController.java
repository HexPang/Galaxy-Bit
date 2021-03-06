package com.ruoyi.galaxy.controller;

import java.io.File;
import java.io.IOException;
import java.util.*;

import be.christophedetroyer.torrent.Torrent;
import be.christophedetroyer.torrent.TorrentParser;
import com.dampcake.bencode.Bencode;
import com.dampcake.bencode.Type;
import com.ruoyi.common.config.RuoYiConfig;
import com.ruoyi.common.core.domain.entity.SysRole;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.common.utils.file.FileUploadUtils;
import com.ruoyi.common.utils.sign.Base64;
import com.ruoyi.common.utils.uuid.UUID;
import com.ruoyi.galaxy.domain.GlxTorrentAttachment;
import com.ruoyi.galaxy.domain.GlxTorrentFile;
import com.ruoyi.galaxy.domain.GlxTorrentPurchase;
import com.ruoyi.galaxy.service.IGlxTorrentAttachmentService;
import com.ruoyi.galaxy.service.IGlxTorrentFileService;
import com.ruoyi.galaxy.service.IGlxTorrentPurchaseService;
import com.ruoyi.galaxy.vo.AttachmentVO;
import com.ruoyi.system.service.ISysPostService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.galaxy.domain.GlxTorrent;
import com.ruoyi.galaxy.service.IGlxTorrentService;
import com.ruoyi.common.utils.poi.ExcelUtil;
import com.ruoyi.common.core.page.TableDataInfo;
import org.springframework.web.multipart.MultipartFile;

/**
 * ????????????Controller
 * 
 * @author HexPang
 * @date 2021-03-17
 */
@RestController
@RequestMapping("/galaxy/torrent")
public class GlxTorrentController extends BaseController
{
    @Autowired
    private IGlxTorrentService glxTorrentService;

    @Autowired
    private IGlxTorrentFileService torrentFileService;

    @Autowired
    private IGlxTorrentAttachmentService attachmentService;

    private List<GlxTorrentFile> getDuplicateFiles (Torrent torrent) {
        Map<String, List<Object>> data = new HashMap<>();
        List<Object> nameList = new ArrayList<>();
        List<Object> sizeList = new ArrayList<>();
        if (torrent.getFileList() != null) {
            torrent.getFileList().forEach(x -> {
                nameList.add(x.getFileDirs().get(x.getFileDirs().size() - 1));
                sizeList.add(x.getFileLength());
            });
        } else {
            nameList.add(torrent.getName());
            sizeList.add(torrent.getTotalSize());
        }
        data.put("fileName", nameList);
        data.put("fileSize", sizeList);
        return torrentFileService.selectGlxTorrentFileByFile(data);
    }

    private String uploadPathToLocal(String path) {
        String filePath = RuoYiConfig.getUploadPath();
        path = filePath + path.substring(path.indexOf("/upload") + 7);
        return path;
    }

    private String prepareTorrentBuffer(String torrentPath, boolean attachment, String seed) throws IOException {
        Torrent bitTorrent = getTorrent(torrentPath);
        Bencode bencode = new Bencode(true);
        byte[] fileBytes = org.apache.commons.io.FileUtils.readFileToByteArray(new File(uploadPathToLocal(torrentPath)));
        Map<String, Object> fileMap = bencode.decode(fileBytes, Type.DICTIONARY);

        if (fileMap != null) {
            String announce = this.getUrl() + "/announce?token=" + getUser().getToken() + "&seed=" + seed;
            fileMap.remove("announce-list");
            fileMap.put("announce", announce);
            fileMap.put("publisher-url", "https://www.galaxy-bit.com:9999/");
            byte[] fileBuffer = bencode.encode(fileMap);
            return Base64.encode(fileBuffer);
        }

        return null;
    }

    @GetMapping("/torrent/seed")
    public AjaxResult downloadSeed(Long id) throws IOException {
        GlxTorrent torrent = glxTorrentService.selectGlxTorrentById(id);
        if (torrent.getUserId() != getUser().getUserId()) {
            return AjaxResult.error("??????????????????");
        }
        String buffer = prepareTorrentBuffer(torrent.getFileName(), false, torrent.getUploadToken());
        if (buffer != null) {
            return AjaxResult.success(torrent.getFileName().substring(torrent.getFileName().lastIndexOf("/") + 1), buffer);
        }
        return AjaxResult.error("????????????");
    }

    /**
     * ??????????????????
     */
    @PostMapping("/upload")
    public AjaxResult uploadFile(MultipartFile file) throws Exception
    {
        if (!file.getOriginalFilename().endsWith(".torrent")) {
            return AjaxResult.error("????????????????????????");
        }
        try
        {
            Torrent torrent = TorrentParser.parseTorrent(file.getInputStream());
            List<GlxTorrent> torrents = glxTorrentService.selectGlxTorrentList(new GlxTorrent(){{
                setInfoHash(torrent.getInfo_hash());
            }});
            if (torrents.size() > 0) {
                return AjaxResult.error("???????????????.");
            }
            List<GlxTorrentFile> dFiles = getDuplicateFiles(torrent);
            torrent.setPiecesBlob(null);
            torrent.setPieces(null);
            torrent.setAnnounce(this.getUrl() + "/announce?token=" + SecurityUtils.getLoginUser().getUser().getToken());
            // ??????????????????
            String filePath = RuoYiConfig.getUploadPath();
            // ??????????????????????????????
            String fileName = FileUploadUtils.upload(filePath, file);
            String url = this.getUrl() + fileName;
            AjaxResult ajax = AjaxResult.success();
            ajax.put("fileName", fileName);
            ajax.put("url", url);
            ajax.put("torrent", torrent);
            ajax.put("duplicate", dFiles);
            return ajax;
        }
        catch (Exception e)
        {
            return AjaxResult.error(e.getMessage());
        }
    }

    /**
     * ????????????????????????
     */
    @PreAuthorize("@ss.hasPermi('galaxy:torrent:list')")
    @GetMapping("/list")
    public TableDataInfo list(GlxTorrent glxTorrent)
    {
        startPage();
        if (!getUser().isAdmin()) {
            glxTorrent.setUserId(getUser().getUserId());
        }
        List<GlxTorrent> list = glxTorrentService.selectGlxTorrentList(glxTorrent);
        return getDataTable(list);
    }

    /**
     * ????????????????????????
     */
    @PreAuthorize("@ss.hasPermi('galaxy:torrent:verify')")
    @GetMapping("/review/list")
    public TableDataInfo auditList(GlxTorrent glxTorrent)
    {
        startPage();
        glxTorrent.setStatus("0");
        List<GlxTorrent> list = glxTorrentService.selectGlxTorrentList(glxTorrent);
        return getDataTable(list);
    }

    /**
     * ????????????????????????
     */
    @PreAuthorize("@ss.hasPermi('galaxy:torrent:export')")
    @Log(title = "????????????", businessType = BusinessType.EXPORT)
    @GetMapping("/export")
    public AjaxResult export(GlxTorrent glxTorrent)
    {
        List<GlxTorrent> list = glxTorrentService.selectGlxTorrentList(glxTorrent);
        ExcelUtil<GlxTorrent> util = new ExcelUtil<GlxTorrent>(GlxTorrent.class);
        return util.exportExcel(list, "torrent");
    }

    /**
     * ??????????????????????????????
     */
    @PreAuthorize("@ss.hasPermi('galaxy:torrent:query')")
    @GetMapping(value = "/{id}")
    public AjaxResult getInfo(@PathVariable("id") Long id) throws IOException {
        SysUser user = SecurityUtils.getLoginUser().getUser();
        GlxTorrent glxTorrent = glxTorrentService.selectGlxTorrentById(id);
        if (glxTorrent == null) {
            return AjaxResult.error("???????????????");
        }
        if (!glxTorrent.getUserId().equals(user.getUserId()) && !getUser().isAdmin()) {
            //??????????????????
            return AjaxResult.error("??????????????????");
        }
        String torrentPath = glxTorrent.getFileName();
        Torrent torrent = getTorrent(torrentPath);
        glxTorrent.setRemark(user.getToken());
        torrent.setAnnounce(this.getUrl() + "/announce?token=" + user.getToken());
        glxTorrent.setTorrent(torrent);
        List<AttachmentVO> vo = new ArrayList();
        attachmentService.selectGlxTorrentAttachmentByTorrentId(id).forEach(x -> {
            try {
                AttachmentVO attachmentVO = new AttachmentVO();
                attachmentVO.setId(x.getId());
                attachmentVO.setName(x.getTitle());
                attachmentVO.setUrl(x.getFileName());
                attachmentVO.setTorrent(getTorrent(x.getFileName()));
                vo.add(attachmentVO);
            } catch (IOException e) {

            }
        });
        glxTorrent.setAttachment(vo);
        return AjaxResult.success(glxTorrent);
    }

    /**
     * ????????????
     * @param torrentPath
     * @return
     * @throws IOException
     */
    private Torrent getTorrent(String torrentPath) throws IOException {
        String filePath = RuoYiConfig.getUploadPath();
        torrentPath = filePath + torrentPath.substring(torrentPath.indexOf("/upload") + 7);
        Torrent torrent = TorrentParser.parseTorrent(torrentPath);
        torrent.setPieces(null);
        torrent.setPiecesBlob(null);
        return torrent;
    }

    private GlxTorrent fillTorrentData (GlxTorrent glxTorrent) throws IOException {
        Torrent torrent = getTorrent(glxTorrent.getFileName());
        glxTorrent.setTorrent(torrent);
        long fileSize = 0;
        if(torrent.getFileList() != null) {
            for(int i = 0 ; i < torrent.getFileList().size(); i++) {
                fileSize += torrent.getFileList().get(i).getFileLength();
            }
        } else {
            fileSize = torrent.getTotalSize();
        }

        if (!StringUtils.isNotEmpty(glxTorrent.getUploadToken())) {
            glxTorrent.setUploadToken(UUID.fastUUID().toString());
        }
        if (!StringUtils.isNotEmpty(glxTorrent.getDownloadToken())) {
            glxTorrent.setDownloadToken(UUID.fastUUID().toString());
        }
        glxTorrent.setFileSize(fileSize);
        glxTorrent.setInfoHash(torrent.getInfo_hash());
        glxTorrent.setCreateBy(SecurityUtils.getUsername());
        return glxTorrent;
    }

    private void insertTorrentFiles(GlxTorrent torrent) {
        Torrent t = torrent.getTorrent();
        if (t.getFileList() != null) {
            t.getFileList().forEach(x -> {
                GlxTorrentFile gtf = new GlxTorrentFile();
                gtf.setFileName(x.getFileDirs().get(x.getFileDirs().size() - 1));
                gtf.setFileSize(x.getFileLength());
                gtf.setTorrentId(torrent.getId());
                torrentFileService.insertGlxTorrentFile(gtf);
            });
        } else {
            GlxTorrentFile gtf = new GlxTorrentFile();
            gtf.setFileName(torrent.getTorrent().getName());
            gtf.setFileSize(torrent.getTorrent().getTotalSize());
            gtf.setTorrentId(torrent.getId());
            torrentFileService.insertGlxTorrentFile(gtf);
        }
    }

    private boolean shouldReview() {
        if (getUser().isAdmin()) {
            return false;
        }

        List<SysRole> roles = getUser().getRoles();
        if (roles == null || roles.size() == 0) return true;
        for(int i = 0 ; i < roles.size(); i ++) {
            if (roles.get(i).getRoleKey().equals("user_plus")) {
                return false;
            }
        }
        return true;
    }

    /**
     * ??????????????????
     */
    @PreAuthorize("@ss.hasPermi('galaxy:torrent:add')")
    @Log(title = "????????????", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody GlxTorrent glxTorrent) throws Exception {
        torrentDataVerify(glxTorrent);
        glxTorrent.setUserId(getUser().getUserId());
        glxTorrent.setUploaded(0L);
        glxTorrent.setDownloaded(0L);
        glxTorrent.setTotalDownload(0L);
        if (!shouldReview() && StringUtils.isNotEmpty(glxTorrent.getStatus())) {
            if (glxTorrent.getStatus().equals("0") || glxTorrent.getStatus().equals("1")) {
                glxTorrent.setStatus(glxTorrent.getStatus());
            }
        } else {
            glxTorrent.setStatus("0");
        }
        glxTorrent = fillTorrentData(glxTorrent);
        int ret = glxTorrentService.insertGlxTorrent(glxTorrent);
        insertTorrentFiles(glxTorrent);
        attachment(glxTorrent, false);
        return toAjax(ret);
    }

    private void attachment(GlxTorrent torrent, boolean isUpdate) throws IOException {
        if (torrent.getAttachment() == null) {
            return;
        }
        if (isUpdate) {
            attachmentService.deleteGlxTorrentAttachmentByTorrentId(torrent.getId());
        }
        for(int i = 0 ; i < torrent.getAttachment().size(); i++) {
            AttachmentVO x = torrent.getAttachment().get(i);
            Torrent t = getTorrent(x.getUrl());
            GlxTorrentAttachment attachment = new GlxTorrentAttachment();
            attachment.setFileName(x.getUrl());
            attachment.setTitle(x.getName());
            attachment.setTorrentId(torrent.getId());
            attachment.setInfoHash(t.getInfo_hash());
            attachment.setUserId(torrent.getUserId());
            attachment.setCreateBy(getUser().getUserName());
            if (t.getTotalSize() == null) {
                long size = 0L;
                for (int j = 0 ; j < t.getFileList().size(); j++) {
                    size += t.getFileList().get(j).getFileLength();
                }
                t.setTotalSize(size);
            }
            attachment.setTorrentSize(t.getTotalSize());
            attachmentService.insertGlxTorrentAttachment(attachment);
        }
    }

    /**
     * ??????????????????
     */
    @PreAuthorize("@ss.hasPermi('galaxy:torrent:verify')")
    @Log(title = "????????????", businessType = BusinessType.UPDATE)
    @GetMapping("/verify")
    public AjaxResult verify(Long torrentId, String verify, String msg) throws IOException {
        GlxTorrent torrent = glxTorrentService.selectGlxTorrentById(torrentId);
        if (torrent.getStatus().equals("1")) {
            return AjaxResult.error("?????????");
        }
        torrent.setRemark(msg);
        torrent.setStatus(verify);
        return toAjax(glxTorrentService.updateGlxTorrent(torrent));
    }

    private void torrentDataVerify (GlxTorrent torrent) throws Exception {
        if (StringUtils.isEmpty(torrent.getFileName())) {
            throw new Exception("????????????????????????.");
        }
        if (StringUtils.isEmpty(torrent.getThumburl())) {
            throw new Exception("?????????????????????.");
        }
        if (StringUtils.isEmpty(torrent.getDescription())) {
            throw new Exception("????????????????????????.");
        }
    }

    /**
     * ??????????????????
     */
    @PreAuthorize("@ss.hasPermi('galaxy:torrent:edit')")
    @Log(title = "????????????", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody GlxTorrent glxTorrent) throws Exception {
        torrentDataVerify(glxTorrent);
        String status = glxTorrent.getStatus();
        GlxTorrent torrent = glxTorrentService.selectGlxTorrentById(glxTorrent.getId());
        if (!torrent.getUserId().equals(getUser().getUserId()) && !getUser().isAdmin()) {
            return AjaxResult.error("??????????????????");
        }
        glxTorrent.setStatus("0");
        if (!shouldReview() && StringUtils.isNotEmpty(status)) {
            if (status.equals("1")) {
                glxTorrent.setStatus(status);
            }
        }
        if (glxTorrent.getRemark() != null && glxTorrent.getRemark().equalsIgnoreCase(getUser().getToken())) {
            glxTorrent.setRemark(null);
        }
        attachment(glxTorrent, true);
        return toAjax(glxTorrentService.updateGlxTorrent(fillTorrentData(glxTorrent)));
    }

    /**
     * ??????????????????
     */
    @PreAuthorize("@ss.hasPermi('galaxy:torrent:remove')")
    @Log(title = "????????????", businessType = BusinessType.DELETE)
	@DeleteMapping("/{ids}")
    public AjaxResult remove(@PathVariable Long[] ids)
    {
        if (!getUser().isAdmin()) {
            for (Long id:
                 ids) {
                GlxTorrent torrent = glxTorrentService.selectGlxTorrentById(id);
                if (!torrent.getUserId().equals(getUser().getUserId())) {
                    return AjaxResult.error("??????????????????");
                }
            }
        }
        return toAjax(glxTorrentService.deleteGlxTorrentByIds(ids));
    }
}
