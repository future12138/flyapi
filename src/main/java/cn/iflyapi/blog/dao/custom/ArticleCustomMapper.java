package cn.iflyapi.blog.dao.custom;

import cn.iflyapi.blog.entity.Article;
import cn.iflyapi.blog.entity.ArticleExample;
import cn.iflyapi.blog.entity.ArticleWithBLOBs;
import cn.iflyapi.blog.pojo.po.ArticleStats;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface ArticleCustomMapper {

    int addNum(ArticleStats articleStats);
}