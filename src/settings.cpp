/*
   Copyright [2011] [Yao Yuan(yeaya@163.com)]

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/

#include "settings.h"
#include "util.h"
#include "log.h"
#include "tinyxml.h"
#include <boost/filesystem.hpp>

Settings settings_;

Settings::Settings() {
	init();
}

Settings::~Settings() {
	while (!ext_mime_list.empty()) {
		Extension_Mime_Item* item = ext_mime_list.pop_front();
		ext_mime_map.remove(item);
		delete item;
		item = NULL;
	}

	while (!gzip_mime_list.empty()) {
		Gzip_Mime_Type_Item* item = gzip_mime_list.pop_front();
		gzip_mime_map.remove(item);
		delete item;
		item = NULL;
	}
}

void Settings::init() {
	boost::system::error_code ec;
	boost::filesystem::path scp = boost::filesystem::system_complete(boost::filesystem::path("../"), ec);
	if (ec) {
		scp = boost::filesystem::system_complete(boost::filesystem::current_path(ec), ec);
	}
	if (!ec) {
		home_dir = scp.string();
	}

	port = 7788;
	inter = "0.0.0.0";
	maxbytes = 768 * 1024 * 1024;
	maxconns = 1024;
	factor = 1.25;
	pool_size = 2;
	num_threads = 4;
	item_size_min = 48;
	item_size_max = 5 * 1024 * 1024;

	max_stats_group = 1024;

	default_cache_expiration = 600;
	manager_base_url = "/manager/";

	min_gzip_size = 512;
	max_gzip_size = 16777216;

	string mime_type = "text/html";
	default_mime_type_length = mime_type.size();
	memcpy(default_mime_type, mime_type.c_str(), default_mime_type_length);
	default_mime_type[default_mime_type_length] = '\0';
}

string Settings::load_conf() {
	string xmlfile = settings_.home_dir + "conf/web.xml";
	TiXmlDocument doc(xmlfile.c_str());
	if (!doc.LoadFile()) {
		return "[web.xml] load error";
	}

	TiXmlHandle hDoc(&doc);
	TiXmlHandle hRoot(0);

	TiXmlElement* first = hDoc.FirstChildElement().Element();
	if (first == NULL) {
		return "[web.xml] format error";
	}
	string name = first->Value();

	hRoot = TiXmlHandle(first);

	TiXmlElement* ele = hRoot.FirstChildElement("default-cache-expiration").Element();
	if (ele != NULL) {
		if (ele->GetText() != NULL) {
			string t = ele->GetText();
			if (!safe_toui32(t.c_str(), t.size(), default_cache_expiration)) {
				return "[web.xml] reading default-cache-expiration error";
			}
		}
	}

	ele = hRoot.FirstChildElement("manager-base-url").Element();
	if (ele != NULL) {
		if (ele->GetText() != NULL) {
			string t = ele->GetText();
			if (t.size() > 0) {
				if (t.at(0) != '/') {
					t = "/" + t;
				}
				if (t.at(t.size() - 1) != '/') {
					t = t + "/";
				}
				manager_base_url = t;
			}
		} else {
			return "[web.xml] reading manager-base-url error";
		}
	}

	TiXmlElement* mime = hRoot.FirstChild("mime-mapping").Element();
	while (mime != NULL) {
		const char* ext = NULL;
		const char* type = NULL;
		ele = mime->FirstChildElement("extension");
		if (ele != NULL) {
			ext = ele->GetText();
		}
		ele = mime->FirstChildElement("mime-type");
		if (ele != NULL) {
			type = ele->GetText();
		}
		if (ext != NULL && type != NULL) {
			string str_ext = string(ext);
			string str_type = string(type);

			Extension_Mime_Item* item = new Extension_Mime_Item();
			item->externsion.set((uint8_t*)str_ext.c_str(), str_ext.size());
			item->mime_type.set((uint8_t*)str_type.c_str(), str_type.size());

			ext_mime_list.push_back(item);
			ext_mime_map.insert(item, item->externsion.hash_value());
		}
		mime = mime->NextSiblingElement();
	}

	ele = hRoot.FirstChildElement("default-mime-type").Element();
	if (ele != NULL) {
		if (ele->GetText() != NULL) {
			string mime_type = ele->GetText();
			if (!mime_type.empty() && mime_type.size() <= MAX_MIME_TYPE_LENGTH) {
				default_mime_type_length = mime_type.size();
				memcpy(default_mime_type, mime_type.c_str(), default_mime_type_length);
				default_mime_type[default_mime_type_length] = '\0';
			}
		}
	}

	TiXmlElement* gzip = hRoot.FirstChild("gzip").Element();
	if (gzip != NULL) {
		string enable;
		const char* type = NULL;
		ele = gzip->FirstChildElement("enable");
		if (ele != NULL && ele->GetText() != NULL) {
			enable = ele->GetText();
		}
		if (enable == "true") {
			ele = gzip->FirstChildElement("min-size");
			if (ele != NULL && ele->GetText() != NULL) {
				string t = ele->GetText();
				if (!safe_toui32(t.c_str(), t.size(), min_gzip_size)) {
					return "[web.xml] reading gzip.min-size error";
				}
			}
			ele = gzip->FirstChildElement("max-size");
			if (ele != NULL && ele->GetText() != NULL) {
				string t = ele->GetText();
				if (!safe_toui32(t.c_str(), t.size(), max_gzip_size)) {
					return "[web.xml] reading gzip.max-size error";
				}
			}
			ele = gzip->FirstChildElement("mime-type");
			while (ele != NULL) {
				if (ele->GetText() != NULL) {
					type = ele->GetText();
					string str_type = string(type);
					Gzip_Mime_Type_Item* item = new Gzip_Mime_Type_Item();
					item->mime_type.set((uint8_t*)str_type.c_str(), str_type.size());
					gzip_mime_list.push_back(item);
					gzip_mime_map.insert(item, item->mime_type.hash_value());
				}
				ele = ele->NextSiblingElement();
			}
		}
	}

	TiXmlElement* welcome = hRoot.FirstChild("welcome-file-list").FirstChildElement("welcome-file").Element();
	while (welcome != NULL) {
		const char* file = welcome->GetText();
		if (file != NULL) {
			welcome_file_list.push_back(string(file));
		}
		welcome = welcome->NextSiblingElement();
	}

	return "";
}
/*
bool Settings::ext_to_mime(const string& ext, string& mime_type) {
	map<string, string>::iterator it = mime_map.find(ext);
	if (it != mime_map.end()) {
		mime_type = it->second;
		return true;
	}
	return false;
}
*/
const uint8_t* Settings::get_mime_type(const uint8_t* ext, uint32_t ext_size, uint32_t& mime_type_length) {
	Const_Data cd(ext, ext_size);
	Extension_Mime_Item* item = ext_mime_map.find(&cd, cd.hash_value());
	if (item != NULL) {
		mime_type_length = item->mime_type.size;
		return item->mime_type.data;
	}
	mime_type_length = 0;
	return NULL;
}

const char* Settings::get_default_mime_type(uint32_t& mime_type_length) {
	mime_type_length = default_mime_type_length;
	return default_mime_type;
}

bool Settings::is_gzip_mime_type(const uint8_t* mime_type, uint32_t mime_type_length) {
	Const_Data cd(mime_type, mime_type_length);
	return gzip_mime_map.find(&cd, cd.hash_value()) != NULL;
}

void Settings::print() {
	LOG_INFO("BEGIN-----SETTINGS INFO-----BEGIN");
	LOG_INFO("XIXIBASE_HOME=" << home_dir);
	LOG_INFO("maxbytes=" << maxbytes);
	LOG_INFO("maxconns=" << maxconns);
	LOG_INFO("port=" << port);
	LOG_INFO("inter=" << inter);
	LOG_INFO("factor=" << factor);
	LOG_INFO("pool_size=" << pool_size);
	LOG_INFO("num_threads=" << num_threads);
	LOG_INFO("item_size_min=" << item_size_min);
	LOG_INFO("item_size_max=" << item_size_max);
	LOG_INFO("END-----SETTINGS INFO-----END");
}
