package cn.iflyapi.blog.service;

import cn.iflyapi.blog.annotation.OpLog;
import cn.iflyapi.blog.dao.ArticleMapper;
import cn.iflyapi.blog.dao.SubjectMapper;
import cn.iflyapi.blog.dao.custom.ArticleCustomMapper;
import cn.iflyapi.blog.entity.*;
import cn.iflyapi.blog.enums.CodeMsgEnum;
import cn.iflyapi.blog.enums.OperationEnum;
import cn.iflyapi.blog.enums.OrderbyEnum;
import cn.iflyapi.blog.exception.FlyapiException;
import cn.iflyapi.blog.pojo.dto.ArticleDto;
import cn.iflyapi.blog.pojo.dto.RankArticleDto;
import cn.iflyapi.blog.pojo.po.ArticleStats;
import cn.iflyapi.blog.util.HtmlUtils;
import cn.iflyapi.blog.util.SnowflakeIdWorker;
import cn.iflyapi.blog.util.WordUtils;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import org.apdplat.word.WordSegmenter;
import org.apdplat.word.segmentation.Word;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * @author flyhero
 * @date 2018/12/16 6:52 PM
 */
@Service
public class ArticleService {

    public static final int DESC_LIMIT = 50;
    @Autowired
    private ArticleMapper articleMapper;

    @Autowired
    private SubjectMapper subjectMapper;

    @Autowired
    private ArticleCustomMapper articleCustomMapper;

    @Autowired
    private SnowflakeIdWorker idWorker;

    @Autowired
    private OperationService operationService;

    public List<Article> listArticle(Long subjectId, Long userId) {
        SubjectExample subjectExample = new SubjectExample();
        subjectExample.createCriteria().andSubjectIdEqualTo(subjectId).andUserIdEqualTo(userId);
        boolean isOwn = subjectMapper.countByExample(subjectExample) > 0;
        if (!isOwn) {
            Subject subject = subjectMapper.selectByPrimaryKey(subjectId);
            if (Objects.isNull(subject) || subject.getIsDelete()) {
                throw new FlyapiException(CodeMsgEnum.RESOURCE_NOT_EXIST);
            }
            if (subject.getIsPrivate()) {
                throw new FlyapiException(CodeMsgEnum.RESOURCE_FORBIDDEN);
            }
        }
        ArticleExample articleExample = new ArticleExample();
        articleExample.createCriteria().andSubjectIdEqualTo(subjectId).andIsDeleteEqualTo(false);
        return articleMapper.selectByExample(articleExample);
    }

    @OpLog(op = OperationEnum.ARTICLE_READ, score = 2)
    public ArticleWithBLOBs readArticle(Long articleId) {
        ArticleWithBLOBs articleWithBLOBs = articleMapper.selectByPrimaryKey(articleId);
        if (Objects.isNull(articleWithBLOBs) || articleWithBLOBs.getIsDelete()) {
            throw new FlyapiException(CodeMsgEnum.RESOURCE_NOT_EXIST);
        }
        articleCustomMapper.addNum(ArticleStats.view(articleId));
        return articleWithBLOBs;
    }

    public void countLike(Long articleId) {
        articleCustomMapper.addNum(ArticleStats.like(articleId));
    }

    public void countComment(Long articleId) {
        articleCustomMapper.addNum(ArticleStats.comment(articleId));
    }

    public Page<Article> listPageAticles(String title, int orderby, Long userId, int pageNum, int pageSize) {
        ArticleExample articleExample = new ArticleExample();
        ArticleExample.Criteria criteria = articleExample.createCriteria();
        if (!StringUtils.isEmpty(title)) {
            criteria.andTitleLike("%" + title + "%").andIsDeleteEqualTo(false);
            articleExample.setOrderByClause("create_time desc");
        } else {
            if (OrderbyEnum.CREATETIME.getCode() == orderby) {
                articleExample.setOrderByClause("create_time desc");
            } else if (OrderbyEnum.HOT.getCode() == orderby) {
                articleExample.setOrderByClause("like_num,comment_num,view_num desc");
            } else if (OrderbyEnum.PREDICTION.getCode() == orderby) {
                if (Objects.isNull(userId)) {
                    throw new FlyapiException(CodeMsgEnum.USER_NOT_LOGIN);
                }
                String tags = operationService.tagsArticleReadOpLog(userId);
                if (!StringUtils.isEmpty(tags)) {
                    List<String> wordCount = WordUtils.wordCount(userId, tags);
                    for (String tag : wordCount) {
                        articleExample.or(articleExample.createCriteria().andTitleLike("%" + tag + "%").andIsDeleteEqualTo(false));
                    }
                }
                articleExample.setOrderByClause("create_time desc");
            }
        }
        return PageHelper.startPage(pageNum, pageSize)
                .doSelectPage(() -> articleMapper.selectByExample(articleExample));
    }

    public boolean remove(Long articleId) {
        ArticleWithBLOBs article = new ArticleWithBLOBs();
        article.setArticleId(articleId);
        article.setIsDelete(true);
        return articleMapper.updateByPrimaryKeySelective(article) > 0;
    }

    @OpLog(op = OperationEnum.ARTICLE_WRITE, score = 5)
    public boolean save(ArticleDto articleDto, Long userId) {
        ArticleWithBLOBs article = new ArticleWithBLOBs();
        article.setArticleId(idWorker.nextId());
        article.setUserId(userId);
        article.setCreateTime(new Date());
        article.setMdContent(articleDto.getMdContent());
        article.setHtmlContent(articleDto.getHtmlContent());
        article.setCover(getImg(articleDto.getHtmlContent()));

        article.setSubjectId(articleDto.getSubjectId());
        article.setTitle(articleDto.getTitle());

        List<Word> words = WordSegmenter.seg(articleDto.getTitle());
        article.setTags(words.stream().map(Word::getText).collect(Collectors.joining(",")));

        String desc = HtmlUtils.cleanHtmlTag(articleDto.getHtmlContent());
        if (desc.length() > DESC_LIMIT) {
            article.setArticleDes(desc.substring(0, DESC_LIMIT));
        } else {
            article.setArticleDes(desc.substring(0, desc.length() - 1));
        }

        return articleMapper.insertSelective(article) > 0;
    }

    private static final Pattern patternSrc = Pattern.compile("src\\s*=\\s*\"?(.*?)(\"|>|\\s+)");

    private String getImg(String content) {
        String regEx = "(<img.*src\\s*=\\s*(.*?)[^>]*?>)";
        Pattern pattern = Pattern.compile(regEx, Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(content);
        while (matcher.find()) {
            String img = matcher.group();
            final Matcher m = patternSrc.matcher(img);
            if (m.find()) {
                return m.group(1);
            }
        }
        return "";
    }


    public List<Article> rank(int type, int offset, Long userId) {
        RankArticleDto rankArticleDto = new RankArticleDto();
        rankArticleDto.setUserId(userId);
        rankArticleDto.setType(type);
        rankArticleDto.setOffset(offset);
        return articleCustomMapper.rank(rankArticleDto);
    }
}
